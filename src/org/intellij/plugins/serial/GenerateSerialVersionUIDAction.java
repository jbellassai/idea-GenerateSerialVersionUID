package org.intellij.plugins.serial;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiManager;

public final class GenerateSerialVersionUIDAction extends EditorAction {

    public GenerateSerialVersionUIDAction() {
        super(GenerateSerialVersionUIDHandler.INSTANCE);
    }

	@Override
	public void update(Editor editor, Presentation presentation, DataContext dataContext) {
		final Project project = DataKeys.PROJECT.getData(dataContext);
		boolean       visible = false;
		boolean       enabled = false;

		if (project != null) {
			final VirtualFile virtualFile = DataKeys.VIRTUAL_FILE.getData(dataContext);
			final PsiManager  psiManager  = PsiManager.getInstance(project);
			final PsiClass    psiClass    = GenerateSerialVersionUIDHandler.getPsiClass(virtualFile, psiManager, editor);

			visible = GenerateSerialVersionUIDHandler.needsUIDField(psiClass);
			enabled = (visible && !GenerateSerialVersionUIDHandler.hasUIDField(psiClass));
		}

		presentation.setVisible(visible);
		presentation.setEnabled(enabled);
	}
}
