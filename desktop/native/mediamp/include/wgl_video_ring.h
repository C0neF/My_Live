#pragma once

#include <windows.h>
#include <GL/gl.h>
#include <GL/wglext.h>
#include <array>
#include <cstdint>
#include <memory>
#include <vector>

namespace mediampv {

// Pbuffer storage crosses independent GL contexts through WGL_ARB_render_texture.
// The producer context lives with mpv; rebuilding the Compose window only rebinds
// consumer textures. Methods run under the player's frame mutex, except rendering.
class wgl_video_ring final {
public:
    struct frame {
        HPBUFFERARB buffer = nullptr;
        HDC dc = nullptr;
        enum class ownership { free, rendering, ready, consumer } owner = ownership::free;
        int index = 0;
    };

    bool initialize();
    bool supported() const { return context_ != nullptr; }
    bool allocated() const { return current_ != nullptr; }
    bool allocate(int width, int height, uint32_t generation);
    void retire();
    void collect_retired();
    frame *begin_frame();
    void make_current(frame &target);
    void finish_frame();
    void publish(frame &target);
    int latest_index() const;
    bool acquire(uint64_t state, uint64_t *output);
    void release(uint64_t state);
    void shutdown();

private:
    struct generation {
        uint32_t id;
        std::array<frame, 3> frames{};
        int latest = -1;
        int next = 0;
    };
    void destroy(generation &ring);
    std::unique_ptr<generation> current_;
    std::vector<std::unique_ptr<generation>> retired_;
    HDC bootstrap_dc_ = nullptr;
    HGLRC bootstrap_context_ = nullptr;
    HPBUFFERARB anchor_ = nullptr;
    HDC anchor_dc_ = nullptr;
    HGLRC context_ = nullptr;
    int pixel_format_ = 0;
    PFNWGLCREATEPBUFFERARBPROC create_ = nullptr;
    PFNWGLGETPBUFFERDCARBPROC get_dc_ = nullptr;
    PFNWGLRELEASEPBUFFERDCARBPROC release_dc_ = nullptr;
    PFNWGLDESTROYPBUFFERARBPROC destroy_ = nullptr;
    PFNWGLRELEASETEXIMAGEARBPROC release_image_ = nullptr;
};
}
