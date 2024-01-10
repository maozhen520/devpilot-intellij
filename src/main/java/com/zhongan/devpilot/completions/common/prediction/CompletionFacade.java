package com.zhongan.devpilot.completions.common.prediction;

import static com.zhongan.devpilot.completions.common.general.StaticConfig.*;

import com.google.gson.Gson;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.openapi.application.ex.ApplicationUtil;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ObjectUtils;
import com.zhongan.devpilot.completions.common.binary.requests.autocomplete.AutocompleteRequest;
import com.zhongan.devpilot.completions.common.binary.requests.autocomplete.AutocompleteResponse;
import com.zhongan.devpilot.completions.common.binary.requests.autocomplete.ResultEntry;
import com.zhongan.devpilot.completions.common.capabilities.SuggestionsModeService;
import com.zhongan.devpilot.completions.common.inline.CompletionAdjustment;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;

import com.zhongan.devpilot.completions.common.binary.requests.autocomplete.AutocompleteResponse;
import com.zhongan.devpilot.completions.common.capabilities.SuggestionsModeService;
import com.zhongan.devpilot.completions.common.inline.CompletionAdjustment;
import com.zhongan.devpilot.enums.EditorActionEnum;
import com.zhongan.devpilot.enums.SessionTypeEnum;
import com.zhongan.devpilot.gui.toolwindows.DevPilotChatToolWindowFactory;
import com.zhongan.devpilot.gui.toolwindows.chat.DevPilotChatToolWindow;
import com.zhongan.devpilot.integrations.llms.LlmProviderFactory;
import com.zhongan.devpilot.integrations.llms.entity.DevPilotChatCompletionRequest;
import com.zhongan.devpilot.integrations.llms.entity.DevPilotMessage;
import com.zhongan.devpilot.integrations.llms.entity.PerformanceCheckResponse;
import org.apache.commons.httpclient.util.DateUtil;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CompletionFacade {
    private final SuggestionsModeService suggestionsModeService;

    public CompletionFacade(SuggestionsModeService suggestionsModeService) {
        this.suggestionsModeService = suggestionsModeService;
    }

    @Nullable
    public AutocompleteResponse retrieveCompletions(
            CompletionParameters parameters, @Nullable Integer tabSize) {
        try {
            String filename = getFilename(parameters.getOriginalFile().getVirtualFile());
            return ApplicationUtil.runWithCheckCanceled(
                    () ->
                            retrieveCompletions(
                                    parameters.getEditor(), parameters.getOffset(), filename, tabSize, null),
                    ProgressManager.getInstance().getProgressIndicator());
        } catch (Exception e) {
            return null;
        }
    }

    @Nullable
    public AutocompleteResponse retrieveCompletions(
            @NotNull Editor editor,
            int offset,
            @Nullable Integer tabSize,
            @Nullable CompletionAdjustment completionAdjustment) {
        try {
            String filename =
                    getFilename(FileDocumentManager.getInstance().getFile(editor.getDocument()));
            return retrieveCompletions(editor, offset, filename, tabSize, completionAdjustment);
        } catch (Exception e) {
            return null;
        }
    }

    @Nullable
    public static String getFilename(@Nullable VirtualFile file) {
        return ObjectUtils.doIfNotNull(file, VirtualFile::getPath);
    }

    @Nullable
    private AutocompleteResponse retrieveCompletions(
            @NotNull Editor editor,
            int offset,
            @Nullable String filename,
            @Nullable Integer tabSize,
            @Nullable CompletionAdjustment completionAdjustment) {
        Document document = editor.getDocument();

        int begin = Integer.max(0, offset - MAX_OFFSET);
        int end = Integer.min(document.getTextLength(), offset + MAX_OFFSET);
        AutocompleteRequest req = new AutocompleteRequest();
        req.before = document.getText(new TextRange(begin, offset));
        req.after = document.getText(new TextRange(offset, end));
        req.filename = filename;
        req.maxResults = MAX_COMPLETIONS;
        req.regionIncludesBeginning = (begin == 0);
        req.regionIncludesEnd = (end == document.getTextLength());
        req.offset = offset;
        req.line = document.getLineNumber(offset);
        req.character = offset - document.getLineStartOffset(req.line);
        req.indentation_size = tabSize;
        req.sdkPath = getSdkPath(editor);

        if (completionAdjustment != null) {
            completionAdjustment.adjustRequest(req);
        }

        //TODO Simulating request response. Calling OpenAI or returning a value.
        DevPilotMessage devPilotMessage = new DevPilotMessage();
        devPilotMessage.setRole("user");
        String content = EditorActionEnum.CODE_COMPLETIONS.getPrompt().replace("{{offsetCode}}", document.getText(new TextRange(req.before.lastIndexOf("\n"), offset))).replace("{{selectedCode}}", getFileExtension(editor) + " " + req.before);
        devPilotMessage.setContent(content);
        DevPilotChatCompletionRequest request = new DevPilotChatCompletionRequest();
        // list content support update
        request.setMessages(new ArrayList<>() {{
            add(devPilotMessage);
        }});
        final String response = new LlmProviderFactory().getLlmProvider(editor.getProject()).chatCompletion(request);
        AutocompleteResponse autocompleteResponse = new AutocompleteResponse();
        autocompleteResponse.old_prefix = "";
        autocompleteResponse.user_message = new String[]{};
        ResultEntry resultEntry = new ResultEntry();
        resultEntry.new_prefix = response;
        resultEntry.old_suffix = "";
        resultEntry.new_suffix = "";
        ResultEntry[] resultEntries = new ResultEntry[]{resultEntry};
        autocompleteResponse.results = resultEntries;

        if (completionAdjustment != null) {
            completionAdjustment.adjustResponse(autocompleteResponse);
        }
        return autocompleteResponse;
    }

    private String getSdkPath(Editor editor) {
        if (editor == null) {
            return null;
        }

        VirtualFile virtualFile = FileDocumentManager.getInstance().getFile(editor.getDocument());
        if (virtualFile == null
                || virtualFile.getExtension() == null
                || !virtualFile.getExtension().equals("java")) {
            return null;
        }

        if (editor.getProject() == null) {
            return null;
        }

        ProjectRootManager rootManager = ProjectRootManager.getInstance(editor.getProject());
        if (rootManager == null) {
            return null;
        }

        Sdk sdk = rootManager.getProjectSdk();
        if (sdk == null) {
            return null;
        }

        if (!sdk.getSdkType().isLocalSdk(sdk)) {
            return null;
        }

        String homePath = sdk.getHomePath();

        if (!isValidJavaHome(homePath)) {
            return null;
        }

        return homePath;
    }

    private boolean isValidJavaHome(String homePath) {
        Path path = Paths.get(homePath, "bin", "java");
        return Files.exists(path);
    }

    private int determineTimeoutBy(@NotNull String before) {
        if (!suggestionsModeService.getSuggestionMode().isInlineEnabled()) {
            return COMPLETION_TIME_THRESHOLD;
        }

        int lastNewline = before.lastIndexOf("\n");
        String lastLine = lastNewline >= 0 ? before.substring(lastNewline) : "";
        boolean endsWithWhitespacesOnly = lastLine.trim().isEmpty();
        return endsWithWhitespacesOnly ? NEWLINE_COMPLETION_TIME_THRESHOLD : COMPLETION_TIME_THRESHOLD;
    }

    private String getFileExtension(Editor editor) {
        if (editor == null) {
            return null;
        }

        VirtualFile virtualFile = FileDocumentManager.getInstance().getFile(editor.getDocument());
        if (virtualFile == null || virtualFile.getExtension() == null) {
            return null;
        }

        return virtualFile.getExtension();
    }

}
