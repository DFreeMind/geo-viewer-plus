package com.geoviewerplus;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.database.datagrid.DataGrid;
import com.intellij.database.datagrid.GridUtil;
import com.intellij.ui.jcef.JBCefApp;

/** Bridges the custom action into DataGrip's real result-set Geo Viewer. */
public final class OpenGeoViewerAction extends DumbAwareAction {
    @Override
    public ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(AnActionEvent event) {
        DataGrid grid = GridUtil.getDataGrid(event.getDataContext());
        boolean hasGrid = event.getProject() != null && grid != null;
        event.getPresentation().setVisible(hasGrid);
        event.getPresentation().setEnabled(hasGrid && JBCefApp.isSupported() && GeoDataExtractor.hasGeometry(grid));
        if (hasGrid) {
            event.getPresentation().setText("Open Geo Viewer Plus");
            event.getPresentation().setDescription("Show the current table or query result on the Geo Viewer Plus map");
        }
    }

    @Override
    public void actionPerformed(AnActionEvent event) {
        DataGrid grid = GridUtil.getDataGrid(event.getDataContext());
        if (event.getProject() == null || grid == null || !JBCefApp.isSupported()) {
            return;
        }
        CustomGeoViewerContent.show(event, grid);
    }
}
