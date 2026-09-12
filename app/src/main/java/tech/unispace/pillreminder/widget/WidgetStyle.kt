package tech.unispace.pillreminder.widget

/**
 * Цвет и прозрачность виджета — чистая логика без Android, чтобы проверять тестом.
 * Фон виджета — `ImageView` со скруглённым shape: цвет задаётся `setColorFilter`, прозрачность —
 * `setImageAlpha`; оба метода доступны из RemoteViews, а углы при этом не теряются.
 */
object WidgetStyle {
    /** Бирюзовый из палитры приложения — фон по умолчанию. */
    val DEFAULT_COLOR: Int = 0xFF2E7D6F.toInt()

    /**
     * Палитра фона: 16 цветов кружками на экране «Виджет»; подписи для TalkBack — `S.widgetColorNames`
     * по тому же индексу. Первый — цвет по умолчанию.
     */
    val presets: List<Int> = listOf(
        0xFF2E7D6F, 0xFF1F5C51, 0xFF2E7D32, 0xFF558B2F,
        0xFF1565C0, 0xFF3949AB, 0xFF6A1B9A, 0xFFAD1457,
        0xFFC62828, 0xFFEF6C00, 0xFFF9A825, 0xFF6D4C41,
        0xFF546E7A, 0xFF1A2422, 0xFFF4F8F6, 0xFFFFFFFF,
    ).map { it.toInt() }

    /** Старое значение «Авто» (до 1.2.1): при чтении настроек переводится в светлый/тёмный по яркости фона. */
    const val TEXT_AUTO = "auto"
    const val TEXT_LIGHT = "light"
    const val TEXT_DARK = "dark"

    /** Относительная яркость 0…1 (sRGB без гамма-коррекции — для выбора цвета текста хватает). */
    fun luminance(argb: Int): Double {
        val r = (argb shr 16 and 0xFF) / 255.0
        val g = (argb shr 8 and 0xFF) / 255.0
        val b = (argb and 0xFF) / 255.0
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    /** Режим текста, который подходит фону: тёмный фон — светлый текст. */
    fun textModeFor(background: Int): String = if (luminance(background) < 0.5) TEXT_LIGHT else TEXT_DARK

    /** Нужен ли светлый текст: явный режим, иначе (старое «авто») — по яркости фона. */
    fun lightText(background: Int, mode: String): Boolean = when (mode) {
        TEXT_LIGHT -> true
        TEXT_DARK -> false
        else -> luminance(background) < 0.5
    }

    /** Цвет текста: основной или приглушённый, светлый или тёмный. */
    fun textColor(primary: Boolean, light: Boolean): Int = when {
        light && primary -> 0xFFFFFFFF.toInt()
        light -> 0xE6FFFFFF.toInt()
        primary -> 0xFF1A201E.toInt()
        else -> 0xCC1A201E.toInt()
    }

    /** Непрозрачность 0–100 % → альфа 0–255 для `ImageView.setImageAlpha`. */
    fun alpha(opacityPercent: Int): Int = opacityPercent.coerceIn(0, 100) * 255 / 100
}
