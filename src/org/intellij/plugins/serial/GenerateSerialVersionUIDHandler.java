package org.intellij.plugins.serial;

import javax.swing.SwingUtilities;

import org.intellij.plugins.serial.siyeh_ig.fixes.SerialVersionUIDBuilder;
import org.intellij.plugins.serial.siyeh_ig.psiutils.ClassUtils;
import org.intellij.plugins.serial.siyeh_ig.psiutils.SerializationUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLiteral;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiPrefixExpression;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.IncorrectOperationException;

public class GenerateSerialVersionUIDHandler extends EditorWriteActionHandler {

	private static final Logger                   LOGGER   = Logger.getInstance(GenerateSerialVersionUIDHandler.class.getName());
	public  static final EditorWriteActionHandler INSTANCE = new GenerateSerialVersionUIDHandler();
	private static       boolean                  _showing;
	private static final boolean                  m_ignoreSerializableDueToInheritance = false;

	private GenerateSerialVersionUIDHandler() {
        // Nothing to do
	}

	public final void executeWriteAction(@Nullable Editor editor, @Nullable DataContext dataContext) {
        if (editor == null) {
            LOGGER.debug("editor == null");
            displayMessage("No editor found.");
            return;
        }
        if (dataContext == null) {
            LOGGER.debug("dataContext == null");
            displayMessage("No data context.");
            return;
        }

		final Project     project     = DataKeys.PROJECT     .getData(dataContext);
		final VirtualFile virtualFile = DataKeys.VIRTUAL_FILE.getData(dataContext);

		if (project == null) {
			LOGGER.debug("project == null");
			displayMessage("No project found.");
			return;
		}
        if (virtualFile == null) {
            LOGGER.debug("virtualFile == null");
            displayMessage("No file found.");
            return;
        }

		final PsiManager manager  = PsiManager.getInstance(project);
		final PsiClass   psiClass = getPsiClass(virtualFile, manager, editor);

		if (psiClass == null) {
			LOGGER.debug("psiClass == null");
			displayMessage("Not a Java class file.");
			return;
		}

        final long serialVersionUIDValue = SerialVersionUIDBuilder.computeDefaultSUID(psiClass);

		if (needsUIDField(psiClass) && !hasUIDField(psiClass, serialVersionUIDValue)) {
			insertSerialVersionUID(project, virtualFile.getExtension(), psiClass, serialVersionUIDValue);
		}
	}

	@Nullable public static PsiClass getPsiClass(@Nullable VirtualFile virtualFile,
                                                 @NotNull  PsiManager  manager,
                                                 @NotNull  Editor      editor) {
		final PsiFile psiFile = (virtualFile == null) ? null : manager.findFile(virtualFile);

        if (psiFile == null) {
            return null;
        }
        final PsiElement elementAtCaret = psiFile.findElementAt(editor.getCaretModel().getOffset());

        return ClassUtils.findPsiClass(elementAtCaret);
	}

	private static void insertSerialVersionUID(Project project, String extension, PsiClass psiClass, long serial) {
		final PsiElementFactory psiElementFactory = JavaPsiFacade.getInstance(project).getElementFactory();
		final CodeStyleManager  codeStyleManager  = CodeStyleManager.getInstance(project);

		if (psiElementFactory != null && codeStyleManager != null) {
			try {
                final String   fullDeclaration = SerialVersionUIDBuilder.getFullDeclaration(extension, serial);
                final PsiField psiField        = psiElementFactory.createFieldFromText(fullDeclaration, null);

				if (psiField != null) {
                    final PsiField oldPsiField = getUIDField(psiClass);

                    codeStyleManager.reformat(psiField);
                    if (oldPsiField != null) {
                        oldPsiField.replace(psiField);
                    } else {
                        psiClass.add(psiField);
                    }
                }
			} catch (IncorrectOperationException e) {
				LOGGER.info("Could not insert field", e);
			}
		}
	}

	private static void displayMessage(@NotNull final String message) {
		SwingUtilities.invokeLater(new Runnable() {
				public void run() {
					try {
						if (!GenerateSerialVersionUIDHandler._showing) {
							GenerateSerialVersionUIDHandler._showing = true;
							Messages.showErrorDialog(message, "Error");
						}
					} finally {
						GenerateSerialVersionUIDHandler._showing = false;
					}
				}
			});
	}

	public static boolean needsUIDField(@Nullable PsiClass aClass) {
		if (aClass == null) {
			return false;
		}
		if (aClass.isInterface() || aClass.isAnnotationType() || aClass.isEnum()) {
			return false;
		}
		if (aClass instanceof PsiTypeParameter || aClass instanceof PsiAnonymousClass) {
			return false;
		}

		if (m_ignoreSerializableDueToInheritance) {
			if (!SerializationUtils.isDirectlySerializable(aClass)) {
				return false;
			}
		} else if (!SerializationUtils.isSerializable(aClass)) {
			return false;
		}
		return true;
	}

    @Nullable public static PsiField getUIDField(@Nullable PsiClass psiClass) {
        if (psiClass != null) {
            for (final PsiField field : psiClass.getFields()) {
                if (SerialVersionUIDBuilder.isUIDField(field)) {
                    return field;
                }
            }
        }
        return null;
    }

	public static boolean hasUIDField(@Nullable PsiClass psiClass) {
		return hasUIDField(psiClass, SerialVersionUIDBuilder.computeDefaultSUID(psiClass));
	}

	public static boolean hasUIDField(@Nullable PsiClass psiClass, long serialVersionUIDValue) {
		final PsiField field = getUIDField(psiClass);

		if (field != null) {
			PsiExpression initializer = field.getInitializer();
            int           sign        = 1;

            if (initializer instanceof PsiPrefixExpression) {
                final PsiPrefixExpression prefixExpression = (PsiPrefixExpression) initializer;

                if (prefixExpression.getOperationSign().getTokenType() == JavaTokenType.MINUS) {
                    sign = -1;
                }
                initializer = prefixExpression.getOperand();
            }

            final Object literalValue = (initializer instanceof PsiLiteral) ? ((PsiLiteral) initializer).getValue() : null;

			return (literalValue instanceof Long && (((Long) literalValue) * sign) == serialVersionUIDValue);
		}
		return false;
	}
}
