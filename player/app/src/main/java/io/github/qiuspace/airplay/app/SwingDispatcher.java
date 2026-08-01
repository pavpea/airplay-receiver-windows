package io.github.qiuspace.airplay.app;

import javax.swing.SwingUtilities;
import java.lang.reflect.InvocationTargetException;

final class SwingDispatcher {

    private SwingDispatcher() {
    }

    static void dispatch(Runnable action) {
        if (SwingUtilities.isEventDispatchThread()) {
            action.run();
        } else {
            SwingUtilities.invokeLater(action);
        }
    }

    static void dispatchAndWait(Runnable action) {
        if (SwingUtilities.isEventDispatchThread()) {
            action.run();
            return;
        }
        try {
            SwingUtilities.invokeAndWait(action);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while updating the Swing UI", error);
        } catch (InvocationTargetException error) {
            throw new IllegalStateException("Unable to update the Swing UI", error.getCause());
        }
    }
}
