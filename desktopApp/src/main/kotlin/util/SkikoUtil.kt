package util

import androidx.compose.ui.awt.ComposeWindow
import org.jetbrains.skiko.SkiaLayer
import java.awt.Container
import javax.swing.JComponent

internal fun <T : JComponent> findComponent(
    container: Container,
    klass: Class<T>,
): T? {
    val children = container.components
    // Prefer any direct child over a match nested in an earlier sibling.
    return children.firstOrNull(klass::isInstance)?.let(klass::cast)
        ?: children.firstNotNullOfOrNull { child -> if (child is Container) findComponent(child, klass) else null }
}

internal inline fun <reified T : JComponent> Container.findComponent() =
    findComponent(this, T::class.java)

internal fun ComposeWindow.findSkiaLayer(): SkiaLayer? = findComponent<SkiaLayer>()
