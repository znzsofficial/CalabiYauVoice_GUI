package util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.awt.ComposeWindow
import org.jetbrains.skiko.SkiaLayer
import java.awt.Component
import java.awt.Color
import java.awt.Container
import java.awt.event.ContainerAdapter
import java.awt.event.ContainerEvent
import javax.swing.JComponent

internal fun <T : JComponent> findComponent(
    container: Container,
    klass: Class<T>,
): T? {
    val componentSequence = container.components.asSequence()
    return componentSequence.filter { klass.isInstance(it) }.ifEmpty {
        componentSequence.filterIsInstance<Container>()
            .mapNotNull { findComponent(it, klass) }
    }.map { klass.cast(it) }.firstOrNull()
}

internal inline fun <reified T : JComponent> Container.findComponent() =
    findComponent(this, T::class.java)

internal fun ComposeWindow.findSkiaLayer(): SkiaLayer? = findComponent<SkiaLayer>()

internal fun ComposeWindow.configureTransparentSkiaLayer(): Boolean {
    val skiaLayer = findSkiaLayer() ?: return false
    val transparent = Color(0, 0, 0, 0)

    contentPane.background = transparent
    (contentPane as? JComponent)?.isOpaque = false
    skiaLayer.isOpaque = false
    skiaLayer.background = transparent
    skiaLayer.transparency = true

    return true
}

@Composable
internal fun rememberTransparentSkiaLayerReady(window: ComposeWindow): Boolean {
    var ready by remember(window) { mutableStateOf(window.configureTransparentSkiaLayer()) }

    DisposableEffect(window) {
        if (ready) {
            return@DisposableEffect onDispose { }
        }

        val listener = object : ContainerAdapter() {
            override fun componentAdded(event: ContainerEvent) {
                if (ready) return
                if (event.child.containsSkiaLayer() && window.configureTransparentSkiaLayer()) {
                    ready = true
                    window.removeSkiaLayerContainerListener(this)
                }
            }
        }

        window.addSkiaLayerContainerListener(listener)
        if (window.configureTransparentSkiaLayer()) {
            ready = true
            window.removeSkiaLayerContainerListener(listener)
        }

        onDispose {
            window.removeSkiaLayerContainerListener(listener)
        }
    }

    return ready
}

private fun Component.containsSkiaLayer(): Boolean =
    this is SkiaLayer || (this as? Container)?.findComponent<SkiaLayer>() != null

private fun Container.addSkiaLayerContainerListener(listener: ContainerAdapter) {
    addContainerListener(listener)
    components.filterIsInstance<Container>().forEach { it.addSkiaLayerContainerListener(listener) }
}

private fun Container.removeSkiaLayerContainerListener(listener: ContainerAdapter) {
    removeContainerListener(listener)
    components.filterIsInstance<Container>().forEach { it.removeSkiaLayerContainerListener(listener) }
}
