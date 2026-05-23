package com.hanzg.mipass.utils

import android.content.Context
import androidx.compose.ui.graphics.Color
import java.util.concurrent.ConcurrentHashMap

data class IconInfo(
    val letter: String,
    val color: Color,
    val resName: String?,
    val resId: Int = 0
)

object IconMatcher {

    // 品牌 → 图标资源名（res/drawable/ic_brand_*.xml）
    private val brandIconMap: Map<String, String> = mapOf(
        // 国内主流
        "wechat" to "ic_brand_wechat", "微信" to "ic_brand_wechat",
        "alipay" to "ic_brand_alipay", "支付宝" to "ic_brand_alipay",
        "qq" to "ic_brand_qq",
        "douyin" to "ic_brand_douyin", "抖音" to "ic_brand_douyin",
        "taobao" to "ic_brand_taobao", "淘宝" to "ic_brand_taobao",
        "weibo" to "ic_brand_weibo", "微博" to "ic_brand_weibo",
        "baidu" to "ic_brand_baidu", "百度" to "ic_brand_baidu",
        "jd" to "ic_brand_jd", "京东" to "ic_brand_jd",
        "meituan" to "ic_brand_meituan", "美团" to "ic_brand_meituan",
        "zhihu" to "ic_brand_zhihu", "知乎" to "ic_brand_zhihu",
        "bilibili" to "ic_brand_bilibili", "b站" to "ic_brand_bilibili",
        "xiaohongshu" to "ic_brand_xiaohongshu", "小红书" to "ic_brand_xiaohongshu",
        "pinduoduo" to "ic_brand_pinduoduo", "拼多多" to "ic_brand_pinduoduo",
        "kuaishou" to "ic_brand_kuaishou", "快手" to "ic_brand_kuaishou",
        "netease" to "ic_brand_netease", "网易" to "ic_brand_netease",
        "dingtalk" to "ic_brand_dingtalk", "钉钉" to "ic_brand_dingtalk",
        "eleme" to "ic_brand_eleme", "饿了么" to "ic_brand_eleme",
        "didichuxing" to "ic_brand_didi", "滴滴" to "ic_brand_didi",
        "alibaba" to "ic_brand_alibaba", "阿里巴巴" to "ic_brand_alibaba",
        "xianyu" to "ic_brand_xianyu", "闲鱼" to "ic_brand_xianyu",
        "ctrip" to "ic_brand_ctrip", "携程" to "ic_brand_ctrip",
        "amap" to "ic_brand_amap", "高德" to "ic_brand_amap", "高德地图" to "ic_brand_amap",
        // 国际主流
        "google" to "ic_brand_google",
        "gmail" to "ic_brand_gmail",
        "youtube" to "ic_brand_youtube",
        "facebook" to "ic_brand_facebook",
        "instagram" to "ic_brand_instagram",
        "twitter" to "ic_brand_twitter", "x" to "ic_brand_twitter",
        "github" to "ic_brand_github",
        "linkedin" to "ic_brand_linkedin",
        "apple" to "ic_brand_apple",
        "microsoft" to "ic_brand_microsoft",
        "amazon" to "ic_brand_amazon",
        "netflix" to "ic_brand_netflix",
        "spotify" to "ic_brand_spotify",
        "discord" to "ic_brand_discord",
        "telegram" to "ic_brand_telegram",
        "whatsapp" to "ic_brand_whatsapp",
        "slack" to "ic_brand_slack",
        "notion" to "ic_brand_notion",
        "paypal" to "ic_brand_paypal",
        "dropbox" to "ic_brand_dropbox",
        "steam" to "ic_brand_steam",
        "reddit" to "ic_brand_reddit",
        "pinterest" to "ic_brand_pinterest",
        "tiktok" to "ic_brand_tiktok",
        "twitch" to "ic_brand_twitch",
        "gitlab" to "ic_brand_gitlab",
        "figma" to "ic_brand_figma",
        "canva" to "ic_brand_canva",
        "zoom" to "ic_brand_zoom",
        "airbnb" to "ic_brand_airbnb",
        "uber" to "ic_brand_uber",
        "shopify" to "ic_brand_shopify",
        "stripe" to "ic_brand_stripe",
        "wordpress" to "ic_brand_wordpress",
        "docker" to "ic_brand_docker",
    )

    private val pinyinMap: Map<Char, String> = mapOf(
        // A
        '阿' to "A", '爱' to "A", '安' to "A", '暗' to "A",
        // B
        '吧' to "B", '白' to "B", '百' to "B", '版' to "B", '宝' to "B", '北' to "B", '本' to "B",
        '比' to "B", '必' to "B", '便' to "B", '标' to "B", '别' to "B", '博' to "B", '不' to "B",
        // C
        '才' to "C", '财' to "C", '采' to "C", '菜' to "C", '产' to "C", '长' to "C", '场' to "C",
        '车' to "C", '成' to "C", '程' to "C", '吃' to "C", '出' to "C", '楚' to "C", '传' to "C",
        '创' to "C", '春' to "C", '词' to "C", '此' to "C", '从' to "C", '存' to "C",
        // D
        '打' to "D", '大' to "D", '代' to "D", '单' to "D", '当' to "D", '到' to "D", '道' to "D",
        '的' to "D", '得' to "D", '等' to "D", '地' to "D", '点' to "D", '电' to "D", '店' to "D",
        '定' to "D", '东' to "D", '动' to "D", '斗' to "D", '都' to "D", '读' to "D", '度' to "D",
        '短' to "D", '段' to "D", '对' to "D", '多' to "D",
        // E
        '饿' to "E", '儿' to "E", '二' to "E",
        // F
        '发' to "F", '法' to "F", '翻' to "F", '反' to "F", '饭' to "F", '方' to "F", '房' to "F",
        '放' to "F", '飞' to "F", '分' to "F", '风' to "F", '服' to "F", '福' to "F", '付' to "F",
        '复' to "F",
        // G
        '改' to "G", '感' to "G", '刚' to "G", '高' to "G", '告' to "G", '歌' to "G", '个' to "G",
        '给' to "G", '根' to "G", '更' to "G", '工' to "G", '公' to "G", '功' to "G", '共' to "G",
        '狗' to "G", '购' to "G", '古' to "G", '故' to "G", '关' to "G", '观' to "G", '管' to "G",
        '光' to "G", '广' to "G", '规' to "G", '国' to "G", '果' to "G", '过' to "G",
        // H
        '还' to "H", '海' to "H", '好' to "H", '号' to "H", '合' to "H", '和' to "H", '黑' to "H",
        '很' to "H", '红' to "H", '后' to "H", '互' to "H", '花' to "H", '华' to "H", '化' to "H",
        '欢' to "H", '环' to "H", '换' to "H", '黄' to "H", '回' to "H", '会' to "H", '活' to "H",
        '火' to "H", '或' to "H",
        // J
        '机' to "J", '积' to "J", '基' to "J", '及' to "J", '即' to "J", '集' to "J", '几' to "J",
        '己' to "J", '计' to "J", '记' to "J", '技' to "J", '际' to "J", '季' to "J", '加' to "J",
        '家' to "J", '间' to "J", '简' to "J", '见' to "J", '件' to "J", '建' to "J", '健' to "J",
        '将' to "J", '讲' to "J", '交' to "J", '角' to "J", '教' to "J", '接' to "J", '街' to "J",
        '节' to "J", '结' to "J", '解' to "J", '介' to "J", '今' to "J", '金' to "J", '近' to "J",
        '进' to "J", '京' to "J", '经' to "J", '精' to "J", '景' to "J", '九' to "J", '久' to "J",
        '酒' to "J", '就' to "J", '局' to "J", '具' to "J", '剧' to "J", '据' to "J", '聚' to "J",
        '决' to "J", '绝' to "J",
        // K
        '开' to "K", '看' to "K", '康' to "K", '考' to "K", '科' to "K", '可' to "K", '克' to "K",
        '客' to "K", '课' to "K", '空' to "K", '口' to "K", '快' to "K", '款' to "K",
        // L
        '拉' to "L", '来' to "L", '老' to "L", '乐' to "L", '了' to "L", '类' to "L", '离' to "L",
        '里' to "L", '理' to "L", '力' to "L", '历' to "L", '立' to "L", '利' to "L", '例' to "L",
        '连' to "L", '联' to "L", '练' to "L", '量' to "L", '聊' to "L", '了' to "L", '林' to "L",
        '零' to "L", '领' to "L", '另' to "L", '流' to "L", '六' to "L", '龙' to "L", '路' to "L",
        '旅' to "L", '绿' to "L", '论' to "L",
        // M
        '买' to "M", '卖' to "M", '满' to "M", '猫' to "M", '么' to "M", '没' to "M", '美' to "M",
        '门' to "M", '们' to "M", '米' to "M", '密' to "M", '免' to "M", '面' to "M", '民' to "M",
        '名' to "M", '明' to "M", '摸' to "M", '模' to "M", '末' to "M", '目' to "M",
        // N
        '拿' to "N", '哪' to "N", '那' to "N", '男' to "N", '南' to "N", '难' to "N", '脑' to "N",
        '呢' to "N", '内' to "N", '能' to "N", '你' to "N", '年' to "N", '念' to "N", '牛' to "N",
        '农' to "N", '女' to "N",
        // P
        '拍' to "P", '排' to "P", '盘' to "P", '旁' to "P", '跑' to "P", '配' to "P", '朋' to "P",
        '批' to "P", '片' to "P", '票' to "P", '品' to "P", '平' to "P", '评' to "P", '破' to "P",
        '普' to "P",
        // Q
        '七' to "Q", '期' to "Q", '其' to "Q", '奇' to "Q", '企' to "Q", '起' to "Q", '气' to "Q",
        '汽' to "Q", '千' to "Q", '前' to "Q", '钱' to "Q", '强' to "Q", '切' to "Q", '亲' to "Q",
        '青' to "Q", '轻' to "Q", '清' to "Q", '情' to "Q", '请' to "Q", '球' to "Q", '区' to "Q",
        '取' to "Q", '去' to "Q", '全' to "Q", '确' to "Q",
        // R
        '然' to "R", '让' to "R", '热' to "R", '人' to "R", '认' to "R", '任' to "R", '日' to "R",
        '容' to "R", '如' to "R", '入' to "R", '软' to "R",
        // S
        '三' to "S", '色' to "S", '沙' to "S", '山' to "S", '商' to "S", '上' to "S", '少' to "S",
        '设' to "S", '社' to "S", '谁' to "S", '身' to "S", '深' to "S", '什' to "S", '生' to "S",
        '声' to "S", '省' to "S", '师' to "S", '十' to "S", '时' to "S", '识' to "S", '实' to "S",
        '食' to "S", '使' to "S", '始' to "S", '世' to "S", '事' to "S", '是' to "S", '收' to "S",
        '手' to "S", '首' to "S", '书' to "S", '输' to "S", '数' to "S", '双' to "S", '水' to "S",
        '睡' to "S", '说' to "S", '思' to "S", '四' to "S", '松' to "S", '送' to "S", '搜' to "S",
        '算' to "S", '随' to "S", '所' to "S",
        // T
        '他' to "T", '她' to "T", '台' to "T", '太' to "T", '态' to "T", '谈' to "T", '淘' to "T",
        '讨' to "T", '特' to "T", '提' to "T", '题' to "T", '体' to "T", '天' to "T", '条' to "T",
        '铁' to "T", '听' to "T", '通' to "T", '同' to "T", '头' to "T", '图' to "T", '推' to "T",
        '退' to "T",
        // W
        '外' to "W", '完' to "W", '玩' to "W", '万' to "W", '王' to "W", '网' to "W", '往' to "W",
        '微' to "W", '为' to "W", '围' to "W", '维' to "W", '未' to "W", '位' to "W", '文' to "W",
        '问' to "W", '我' to "W", '无' to "W", '五' to "W", '物' to "W",
        // X
        '西' to "X", '希' to "X", '习' to "X", '喜' to "X", '系' to "X", '下' to "X", '先' to "X",
        '显' to "X", '现' to "X", '线' to "X", '相' to "X", '香' to "X", '想' to "X", '向' to "X",
        '项' to "X", '小' to "X", '效' to "X", '校' to "X", '些' to "X", '写' to "X", '新' to "X",
        '心' to "X", '信' to "X", '星' to "X", '行' to "X", '形' to "X", '性' to "X", '修' to "X",
        '需' to "X", '许' to "X", '选' to "X", '学' to "X", '寻' to "X",
        // Y
        '压' to "Y", '呀' to "Y", '言' to "Y", '研' to "Y", '眼' to "Y", '演' to "Y", '样' to "Y",
        '要' to "Y", '药' to "Y", '也' to "Y", '业' to "Y", '一' to "Y", '衣' to "Y", '医' to "Y",
        '已' to "Y", '以' to "Y", '意' to "Y", '因' to "Y", '音' to "Y", '银' to "Y", '引' to "Y",
        '应' to "Y", '营' to "Y", '影' to "Y", '硬' to "Y", '用' to "Y", '由' to "Y", '有' to "Y",
        '友' to "Y", '又' to "Y", '鱼' to "Y", '与' to "Y", '语' to "Y", '育' to "Y", '预' to "Y",
        '元' to "Y", '原' to "Y", '远' to "Y", '院' to "Y", '约' to "Y", '月' to "Y", '越' to "Y",
        '云' to "Y", '运' to "Y",
        // Z
        '再' to "Z", '在' to "Z", '咱' to "Z", '早' to "Z", '怎' to "Z", '增' to "Z", '展' to "Z",
        '战' to "Z", '站' to "Z", '张' to "Z", '找' to "Z", '照' to "Z", '者' to "Z", '这' to "Z",
        '真' to "Z", '正' to "Z", '政' to "Z", '支' to "Z", '知' to "Z", '直' to "Z", '指' to "Z",
        '只' to "Z", '制' to "Z", '质' to "Z", '治' to "Z", '中' to "Z", '种' to "Z", '重' to "Z",
        '周' to "Z", '主' to "Z", '住' to "Z", '注' to "Z", '专' to "Z", '转' to "Z", '装' to "Z",
        '资' to "Z", '子' to "Z", '自' to "Z", '字' to "Z", '总' to "Z", '走' to "Z", '组' to "Z",
        '最' to "Z", '作' to "Z", '做' to "Z"
    )

    private val brandColors: Map<String, Color> = mapOf(
        "wechat" to Color(0xFF07C160), "微信" to Color(0xFF07C160),
        "alipay" to Color(0xFF1677FF), "支付宝" to Color(0xFF1677FF),
        "taobao" to Color(0xFFFF6A00), "淘宝" to Color(0xFFFF6A00),
        "qq" to Color(0xFF12B7F5), "jd" to Color(0xFFE3393C), "京东" to Color(0xFFE3393C),
        "douyin" to Color(0xFFFF0042), "抖音" to Color(0xFFFF0042),
        "meituan" to Color(0xFFFFC100), "美团" to Color(0xFFFFC100),
        "baidu" to Color(0xFF2932E1), "百度" to Color(0xFF2932E1),
        "weibo" to Color(0xFFE6162D), "微博" to Color(0xFFE6162D),
        "zhihu" to Color(0xFF0084FF), "知乎" to Color(0xFF0084FF),
        "bilibili" to Color(0xFF00A1D6), "b站" to Color(0xFF00A1D6),
        "netease" to Color(0xFFC20C0C), "网易" to Color(0xFFC20C0C),
        "xiaohongshu" to Color(0xFFFF2442), "小红书" to Color(0xFFFF2442),
        "pinduoduo" to Color(0xFFE02E24), "拼多多" to Color(0xFFE02E24),
        "kuaishou" to Color(0xFFFF4906), "快手" to Color(0xFFFF4906),
        "didichuxing" to Color(0xFFFF8C00), "滴滴" to Color(0xFFFF8C00),
        "alibaba" to Color(0xFFFF6A00), "阿里巴巴" to Color(0xFFFF6A00),
        "xianyu" to Color(0xFFFFC300), "闲鱼" to Color(0xFFFFC300),
        "ctrip" to Color(0xFF0066FF), "携程" to Color(0xFF0066FF),
        "eleme" to Color(0xFF0085FF), "饿了么" to Color(0xFF0085FF),
        "google" to Color(0xFF4285F4), "gmail" to Color(0xFFEA4335),
        "facebook" to Color(0xFF1877F2), "twitter" to Color(0xFF1DA1F2),
        "instagram" to Color(0xFFE4405F), "youtube" to Color(0xFFFF0000),
        "tiktok" to Color(0xFF00F2EA), "spotify" to Color(0xFF1DB954),
        "github" to Color(0xFF24292F), "gitlab" to Color(0xFFFC6D26),
        "amazon" to Color(0xFFFF9900), "microsoft" to Color(0xFF5E5E5E),
        "apple" to Color(0xFFA2AAAD), "netflix" to Color(0xFFE50914),
        "paypal" to Color(0xFF003087), "slack" to Color(0xFF4A154B),
        "notion" to Color(0xFFFFFFFF), "telegram" to Color(0xFF26A5E4),
        "discord" to Color(0xFF5865F2), "twitch" to Color(0xFF9146FF),
        "whatsapp" to Color(0xFF25D366), "linkedin" to Color(0xFF0A66C2),
        "dropbox" to Color(0xFF0061FF), "figma" to Color(0xFFF24E1E),
        "canva" to Color(0xFF00C4CC), "zoom" to Color(0xFF2D8CFF),
        "airbnb" to Color(0xFFFF5A5F), "uber" to Color(0xFF2769B3),
        "reddit" to Color(0xFFFF4500), "pinterest" to Color(0xFFE60023),
        "shopify" to Color(0xFF7AB55C), "stripe" to Color(0xFF635BFF),
        "wordpress" to Color(0xFF21759B), "docker" to Color(0xFF2496ED),
        "steam" to Color(0xFF1A9FFF),
    )

    private val hashColors = listOf(
        Color(0xFF3D5A78), Color(0xFF6B8DA8), Color(0xFFC44D34), Color(0xFF4A7C59),
        Color(0xFF8B5E3C), Color(0xFF6C4F8F), Color(0xFF2B7A8C), Color(0xFFD4853C),
        Color(0xFF5C4B51), Color(0xFF3A7D44), Color(0xFFB84C5C), Color(0xFF4E6B8A),
        Color(0xFF8C5E4A), Color(0xFF5E548E), Color(0xFF2F6690), Color(0xFFC97D3A)
    )

    private val resIdCache = ConcurrentHashMap<String, Int>()

    fun getResId(resName: String?, context: Context): Int {
        if (resName == null) return 0
        return resIdCache.getOrPut(resName) {
            context.resources.getIdentifier(resName, "drawable", context.packageName)
        }
    }

    fun resolve(name: String): IconInfo {
        if (name.isBlank()) return IconInfo("?", hashColors[0], null, 0)
        return IconInfo(
            letter = getIconLetterInternal(name),
            color = getIconColorInternal(name),
            resName = getIconResourceInternal(name)
        )
    }

    fun resolveWithContext(name: String, context: Context): IconInfo {
        val info = resolve(name)
        return info.copy(resId = getResId(info.resName, context))
    }

    /** 返回匹配到的品牌图标资源名，未匹配返回 null */
    fun getIconResource(name: String): String? = getIconResourceInternal(name)

    private fun getIconResourceInternal(name: String): String? {
        if (name.isBlank()) return null
        val lower = name.trim().lowercase()
        // Phase 1: O(1) direct token lookup (handles "微信", "google", etc.)
        brandIconMap[lower]?.let { return it }
        // Phase 2: O(n) substring fallback (handles "企业微信", "Google Drive", etc.)
        for ((key, res) in brandIconMap) {
            if (lower.contains(key)) return res
        }
        return null
    }

    fun getIconLetter(name: String): String = getIconLetterInternal(name)

    private fun getIconLetterInternal(name: String): String {
        if (name.isBlank()) return "?"
        val first = name.trim().first()
        return when {
            first in 'A'..'Z' -> first.uppercaseChar().toString()
            first in 'a'..'z' -> first.uppercaseChar().toString()
            first in '0'..'9' -> first.toString()
            else -> {
                val pinyin = pinyinMap[first]
                if (pinyin != null) pinyin
                else first.uppercaseChar().toString()
            }
        }
    }

    fun getIconColor(name: String): Color = getIconColorInternal(name)

    private fun getIconColorInternal(name: String): Color {
        if (name.isBlank()) return hashColors[0]
        val lower = name.trim().lowercase()
        // Phase 1: O(1) direct key lookup
        brandColors[lower]?.let { return it }
        // Phase 2: O(n) substring fallback
        for ((key, color) in brandColors) {
            if (lower.contains(key)) return color
        }
        val hash = name.hashCode()
        return hashColors[(hash and Int.MAX_VALUE) % hashColors.size]
    }
}
