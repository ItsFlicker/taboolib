package taboolib.module.kether.action.game.compat

import me.clip.placeholderapi.PlaceholderAPI
import taboolib.common.platform.Platform
import taboolib.common.platform.PlatformSide
import taboolib.module.kether.*

@PlatformSide([Platform.BUKKIT])
internal object ActionPlaceholder {

    @KetherParser(["papi", "placeholder"])
    internal fun actionPlaceholder() = scriptParser {
        val str = it.nextParsedAction()
        actionTake { run(str).str { s -> PlaceholderAPI.setPlaceholders(player().cast(), s) } }
    }
}