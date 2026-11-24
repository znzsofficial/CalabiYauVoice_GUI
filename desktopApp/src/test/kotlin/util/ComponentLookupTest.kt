package util

import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertSame

class ComponentLookupTest {
    @Test fun directChildrenTakePrecedenceOverEarlierNestedMatches() {
        val root = JPanel()
        val nested = JLabel()
        root.add(JPanel().apply { add(nested) })
        val direct = JLabel()
        root.add(direct)

        assertSame(direct, root.findComponent<JLabel>())
        root.remove(direct)
        assertSame(nested, root.findComponent<JLabel>())
        root.removeAll()
        assertNull(root.findComponent<JLabel>())
    }
}
