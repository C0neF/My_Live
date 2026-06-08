package com.mylive.app.core.common

import com.mylive.app.core.model.LiveMessageSpan

object EmojiParser {
    private val EMOJI_REGEX = Regex("\\[[^\\[\\]]{1,16}\\]")

    private val douyinEmojiMap = mapOf(
        "[V5]" to "clv.png",
        "[给力]" to "clw.png",
        "[嘿哈]" to "cm8.png",
        "[加好友]" to "cm9.png",
        "[勾引]" to "cmt.png",
        "[机智]" to "cn0.png",
        "[来看我]" to "cn1.png",
        "[灵机一动]" to "cn2.png",
        "[困]" to "cna.png",
        "[疑问]" to "cnb.png",
        "[泣不成声]" to "cnc.png",
        "[小鼓掌]" to "cnd.png",
        "[发呆]" to "cnf.png",
        "[吐血]" to "cnj.png",
        "[酷拽]" to "cnq.png",
        "[泪奔]" to "cnv.png",
        "[抠鼻]" to "co1.png",
        "[互粉]" to "co3.png",
        "[去污粉]" to "co8.png",
        "[666]" to "co9.png",
        "[舔屏]" to "cof.png",
        "[鄙视]" to "cog.png",
        "[紫薇别走]" to "coj.png",
        "[不失礼貌的微笑]" to "cop.png",
        "[吐舌]" to "coq.png",
        "[呆无辜]" to "cor.png",
        "[白眼]" to "cot.png",
        "[吃瓜群众]" to "cox.png",
        "[绿帽子]" to "coz.png",
        "[皱眉]" to "cp2.png",
        "[擦汗]" to "cp3.png",
        "[强]" to "cp7.png",
        "[如花]" to "cp8.png",
        "[奋斗]" to "cpc.png",
        "[微笑]" to "1f642.png",
        "[害羞]" to "1f60a.png",
        "[击掌]" to "1f64c.png",
        "[左上]" to "1f446.png",
        "[握手]" to "1f91d.png",
        "[18禁]" to "1f51e.png",
        "[菜刀]" to "1f52a.png",
        "[爱心]" to "2764.png",
        "[心碎]" to "1f494.png",
        "[便便]" to "1f4a9.png",
        "[惊讶]" to "1f632.png",
        "[调皮]" to "1f61b.png",
        "[礼物]" to "1f381.png",
        "[蛋糕]" to "1f382.png",
        "[派对]" to "1f389.png",
        "[不看]" to "1f648.png",
        "[炸弹]" to "1f4a3.png",
        "[憨笑]" to "1f600.png",
        "[悠闲]" to "1f6ac.png",
        "[晕]" to "1f635.png",
        "[囧]" to "1f644.png",
        "[阴险]" to "1f60f.png",
        "[惊恐]" to "1f628.png",
        "[难过]" to "1f641.png",
        "[斜眼]" to "1f612.png",
        "[左哼哼]" to "1f624.png",
        "[右哼哼]" to "1f624-new.png",
        "[咒骂]" to "1f92c.png",
        "[咖啡]" to "2615.png",
        "[西瓜]" to "1f349.png",
        "[衰]" to "1f622.png",
        "[太阳]" to "1f31e.png",
        "[月亮]" to "1f31c.png",
        "[发]" to "1f005.png",
        "[猪头]" to "1f437.png",
        "[凋谢]" to "1f940.png",
        "[红包]" to "1f9e7.png",
        "[拳头]" to "270a.png",
        "[胜利]" to "270c.png",
        "[抱拳]" to "1f64f.png",
        "[闭嘴]" to "1f910.png",
        "[弱]" to "1f44e.png",
        "[左边]" to "1f448.png",
        "[右边]" to "1f449.png",
        "[送心]" to "1f970.png",
        "[耶]" to "270c-new.png",
        "[捂脸]" to "1f926.png",
        "[色]" to "1f60d.png",
        "[打脸]" to "1f915.png",
        "[大笑]" to "1f604.png",
        "[哈欠]" to "1f971.png",
        "[震惊]" to "1f92f.png",
        "[大金牙]" to "1f9b7.png",
        "[偷笑]" to "1f92d.png",
        "[石化]" to "1f630.png",
        "[思考]" to "1f914.png",
        "[可怜]" to "1f97a.png",
        "[嘘]" to "1f92b.png",
        "[撇嘴]" to "1f615.png",
        "[尴尬]" to "1f605.png",
        "[笑哭]" to "1f602.png",
        "[生病]" to "1f637.png",
        "[奸笑]" to "1f60f-new.png",
        "[得意]" to "1f60e.png",
        "[坏笑]" to "1f62c.png",
        "[抓狂]" to "1f62b.png",
        "[钱]" to "1f911.png",
        "[亲亲]" to "1f61a.png",
        "[恐惧]" to "1f631.png",
        "[愉快]" to "1f604-new.png",
        "[玫瑰]" to "1f339.png",
        "[快哭了]" to "1f625.png",
        "[翻白眼]" to "1f644-new.png",
        "[赞]" to "1f44d.png",
        "[鼓掌]" to "1f44f.png",
        "[感谢]" to "1f64f-new.png",
        "[嘴唇]" to "1f444.png",
        "[胡瓜]" to "1f952.png",
        "[流泪]" to "1f622-new.png",
        "[啤酒]" to "1f37a.png",
        "[我想静静]" to "1f611.png",
        "[委屈]" to "1f641-new.png",
        "[飞吻]" to "1f618.png",
        "[再见]" to "1f44b.png",
        "[听歌]" to "1f3a7.png",
        "[发怒]" to "1f621.png",
        "[绝望的凝视]" to "1f61e.png",
        "[看]" to "1f436.png",
        "[熊吉]" to "1f43b.png",
        "[骷髅]" to "1f480.png",
        "[黑脸]" to "1f31a.png",
        "[呲牙]" to "1f601.png",
        "[吐]" to "1f92e.png",
        "[流汗]" to "1f613.png",
        "[摸头]" to "1f60c.png",
        "[红脸]" to "1f633.png",
        "[尬笑]" to "1f605-new.png",
        "[做鬼脸]" to "1f61c.png",
        "[睡]" to "1f62a.png",
        "[惊喜]" to "1f929.png",
        "[敲打]" to "1f915-new.png",
        "[吐彩虹]" to "1f308.png",
        "[大哭]" to "1f62d.png",
        "[比心]" to "1f91e.png",
        "[强壮]" to "1f4aa.png",
        "[碰拳]" to "1f91b.png",
        "[OK]" to "1f44c.png"
    )

    fun parse(text: String, siteId: String): List<LiveMessageSpan> {
        if (text.isEmpty()) return emptyList()
        if (siteId != "douyin") {
            return listOf(LiveMessageSpan.Text(text))
        }

        val spans = mutableListOf<LiveMessageSpan>()
        val matches = EMOJI_REGEX.findAll(text)
        var lastIndex = 0

        for (match in matches) {
            val emojiText = match.value
            val fileName = douyinEmojiMap[emojiText]
            if (fileName != null) {
                if (match.range.first > lastIndex) {
                    spans.add(LiveMessageSpan.Text(text.substring(lastIndex, match.range.first)))
                }
                val assetPath = "file:///android_asset/douyin_emoji/$fileName"
                spans.add(LiveMessageSpan.Image(assetPath))
                lastIndex = match.range.last + 1
            }
        }

        if (lastIndex < text.length) {
            spans.add(LiveMessageSpan.Text(text.substring(lastIndex)))
        }

        return spans.ifEmpty { listOf(LiveMessageSpan.Text(text)) }
    }
}
