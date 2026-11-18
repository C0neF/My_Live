#include "wgl_video_ring.h"
#include "gl_functions_win.h"
#include "log.h"
#include <algorithm>

namespace mediampv {
namespace {
constexpr int buffer_attributes[] = {
    WGL_TEXTURE_FORMAT_ARB, WGL_TEXTURE_RGBA_ARB,
    WGL_TEXTURE_TARGET_ARB, WGL_TEXTURE_2D_ARB,
    WGL_MIPMAP_TEXTURE_ARB, FALSE, 0
};
}

bool wgl_video_ring::initialize() {
    bootstrap_dc_ = wglGetCurrentDC();
    bootstrap_context_ = wglGetCurrentContext();
    auto choose = reinterpret_cast<PFNWGLCHOOSEPIXELFORMATARBPROC>(wglGetProcAddress("wglChoosePixelFormatARB"));
    create_ = reinterpret_cast<PFNWGLCREATEPBUFFERARBPROC>(wglGetProcAddress("wglCreatePbufferARB"));
    get_dc_ = reinterpret_cast<PFNWGLGETPBUFFERDCARBPROC>(wglGetProcAddress("wglGetPbufferDCARB"));
    release_dc_ = reinterpret_cast<PFNWGLRELEASEPBUFFERDCARBPROC>(wglGetProcAddress("wglReleasePbufferDCARB"));
    destroy_ = reinterpret_cast<PFNWGLDESTROYPBUFFERARBPROC>(wglGetProcAddress("wglDestroyPbufferARB"));
    release_image_ = reinterpret_cast<PFNWGLRELEASETEXIMAGEARBPROC>(wglGetProcAddress("wglReleaseTexImageARB"));
    auto bind = wglGetProcAddress("wglBindTexImageARB");
    if (!choose || !create_ || !get_dc_ || !release_dc_ || !destroy_ || !release_image_ || !bind) return false;
    constexpr int pixel_attributes[] = {
        WGL_SUPPORT_OPENGL_ARB, TRUE, WGL_DRAW_TO_PBUFFER_ARB, TRUE,
        WGL_ACCELERATION_ARB, WGL_FULL_ACCELERATION_ARB,
        WGL_PIXEL_TYPE_ARB, WGL_TYPE_RGBA_ARB, WGL_COLOR_BITS_ARB, 32,
        WGL_ALPHA_BITS_ARB, 8, WGL_BIND_TO_TEXTURE_RGBA_ARB, TRUE,
        WGL_DOUBLE_BUFFER_ARB, FALSE, WGL_DEPTH_BITS_ARB, 0, WGL_STENCIL_BITS_ARB, 0, 0
    };
    UINT count = 0;
    if (!choose(bootstrap_dc_, pixel_attributes, nullptr, 1, &pixel_format_, &count) || count == 0) return false;
    anchor_ = create_(bootstrap_dc_, pixel_format_, 1, 1, buffer_attributes);
    if (!anchor_) return false;
    anchor_dc_ = get_dc_(anchor_);
    auto create_context = reinterpret_cast<PFNWGLCREATECONTEXTATTRIBSARBPROC>(wglGetProcAddress("wglCreateContextAttribsARB"));
    constexpr int context_attributes[] = {
        WGL_CONTEXT_MAJOR_VERSION_ARB, 3, WGL_CONTEXT_MINOR_VERSION_ARB, 3,
        WGL_CONTEXT_PROFILE_MASK_ARB, WGL_CONTEXT_CORE_PROFILE_BIT_ARB, 0
    };
    context_ = create_context ? create_context(anchor_dc_, nullptr, context_attributes) : wglCreateContext(anchor_dc_);
    if (!context_ || !wglMakeCurrent(anchor_dc_, context_)) {
        shutdown();
        return false;
    }
    LOGI("MyLive GPU pbuffer transport ready (pixel format %d)", pixel_format_);
    return true;
}

bool wgl_video_ring::allocate(int width, int height, uint32_t generation_id) {
    auto ring = std::make_unique<generation>();
    ring->id = generation_id & 0xFFFFu;
    for (int i = 0; i < 3; ++i) {
        auto &target = ring->frames[i];
        target.index = i;
        target.buffer = create_(bootstrap_dc_, pixel_format_, width, height, buffer_attributes);
        if (!target.buffer) {
            destroy(*ring);
            return false;
        }
        target.dc = get_dc_(target.buffer);
    }
    retire();
    current_ = std::move(ring);
    collect_retired();
    return true;
}

void wgl_video_ring::retire() {
    wglMakeCurrent(anchor_dc_, context_);
    if (current_) retired_.push_back(std::move(current_));
    collect_retired();
}

void wgl_video_ring::destroy(generation &ring) {
    for (auto &target : ring.frames) if (target.buffer) {
        release_dc_(target.buffer, target.dc);
        destroy_(target.buffer);
        target.buffer = nullptr;
    }
}

void wgl_video_ring::collect_retired() {
    std::erase_if(retired_, [this](const auto &ring) {
        const bool held = std::any_of(ring->frames.begin(), ring->frames.end(), [](const frame &f) {
            return f.owner == frame::ownership::consumer;
        });
        if (held) return false;
        destroy(*ring);
        return true;
    });
}

wgl_video_ring::frame *wgl_video_ring::begin_frame() {
    for (int offset = 0; offset < 3; ++offset) {
        const int index = (current_->next + offset) % 3;
        auto &target = current_->frames[index];
        if (target.owner != frame::ownership::free && target.owner != frame::ownership::ready) continue;
        if (current_->latest == index) current_->latest = -1;
        target.owner = frame::ownership::rendering;
        current_->next = (index + 1) % 3;
        return &target;
    }
    return nullptr;
}

void wgl_video_ring::make_current(frame &target) {
    wglMakeCurrent(target.dc, context_);
    gl::bind_framebuffer(GL_FRAMEBUFFER, 0);
}

void wgl_video_ring::finish_frame() {
    // Completion stays on the producer thread; the UI never waits for a video render.
    glFinish();
}

void wgl_video_ring::publish(frame &target) {
    if (current_->latest >= 0) {
        auto &previous = current_->frames[current_->latest];
        if (previous.owner == frame::ownership::ready) previous.owner = frame::ownership::free;
    }
    target.owner = frame::ownership::ready;
    current_->latest = target.index;
}

int wgl_video_ring::latest_index() const { return current_ ? current_->latest : -1; }

bool wgl_video_ring::acquire(uint64_t state, uint64_t *output) {
    if (!current_ || current_->latest < 0) return false;
    auto &target = current_->frames[current_->latest];
    if (target.owner != frame::ownership::ready) return false;
    target.owner = frame::ownership::consumer;
    output[0] = state;
    output[1] = reinterpret_cast<uint64_t>(target.buffer);
    return true;
}

void wgl_video_ring::release(uint64_t state) {
    const auto generation_id = static_cast<uint32_t>(state >> 48);
    const int index = static_cast<int>((state >> 44) & 0xF);
    auto release = [this, generation_id, index](generation &ring) {
        if (ring.id != generation_id) return false;
        auto &target = ring.frames[index];
        release_image_(target.buffer, WGL_FRONT_LEFT_ARB);
        target.owner = frame::ownership::free;
        if (ring.latest == index) ring.latest = -1;
        return true;
    };
    if (current_ && release(*current_)) return;
    for (auto &ring : retired_) if (release(*ring)) return;
}

void wgl_video_ring::shutdown() {
    if (context_) {
        wglMakeCurrent(anchor_dc_, context_);
        glFinish();
        if (current_) destroy(*current_);
        current_.reset();
        for (auto &ring : retired_) destroy(*ring);
        retired_.clear();
        wglMakeCurrent(bootstrap_dc_, bootstrap_context_);
        wglDeleteContext(context_);
        context_ = nullptr;
    }
    if (anchor_) {
        release_dc_(anchor_, anchor_dc_);
        destroy_(anchor_);
        anchor_ = nullptr;
    }
}
}
