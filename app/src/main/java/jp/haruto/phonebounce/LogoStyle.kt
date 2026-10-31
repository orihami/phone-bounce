package jp.haruto.phonebounce

/** ロゴの見た目。どれも、元の Desktop Bounce の DVD ロゴ（斜めの文字＋円盤）を土台にしたバリエーション。 */
enum class LogoStyle(val title: String, val note: String) {
    CLASSIC("クラシック", "元のロゴそのまま"),
    NEON("ネオン", "光る輪郭"),
    CRT("ブラウン管", "走査線と色ズレ"),
    PIXEL("ドット", "8ビット風"),
    OUTLINE("フチどり", "白いフチと影"),
    SPIN("ディスク回転", "円盤に光が走る"),
    IMAGE("画像", "自分のPNGの形"),
}
