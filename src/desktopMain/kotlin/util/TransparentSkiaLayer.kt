package util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.awt.ComposeWindow
import org.jetbrains.skiko.SkiaLayer
import java.awt.Color
import java.awt.Component
import java.awt.Container
import java.awt.event.ContainerAdapter
import java.awt.event.ContainerEvent
import javax.swing.JComponent

/**
 * Makes Compose Desktop's Skia layer transparent without making the decorated AWT frame itself transparent.
 * This is needed for DWM/backdrop effects after Compose 1.11/Skiko stopped clearing component backgrounds
 * as a side effect of [SkiaLayer.transparency].
 */
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

        val installer = SkiaLayerTransparencyInstaller(window) {
            ready = true
        }

        installer.start()
        if (window.configureTransparentSkiaLayer()) {
            ready = true
            installer.stop()
        }

        onDispose {
            installer.stop()
        }
    }

    return ready
}

private class SkiaLayerTransparencyInstaller(
    private val window: ComposeWindow,
    private val onReady: () -> Unit,
) : ContainerAdapter() {
    private var listening = false

    fun start() {
        if (listening) return
        listening = true
        window.addSkiaLayerContainerListener(this)
    }

    fun stop() {
        if (!listening) return
        listening = false
        window.removeSkiaLayerContainerListener(this)
    }

    override fun componentAdded(event: ContainerEvent) {
        if (!listening) return

        (event.child as? Container)?.addSkiaLayerContainerListener(this)
        if (event.child.containsSkiaLayer() && window.configureTransparentSkiaLayer()) {
            onReady()
            stop()
        }
    }

    override fun componentRemoved(event: ContainerEvent) {
        (event.child as? Container)?.removeSkiaLayerContainerListener(this)
    }
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
