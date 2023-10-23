package com.zhongan.codeai.gui.toolwindows.chat;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.componentsList.components.ScrollablePanel;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.ui.components.JBScrollPane;
import com.zhongan.codeai.gui.toolwindows.components.ChatDisplayPanel;
import com.zhongan.codeai.gui.toolwindows.components.ContentComponent;
import com.zhongan.codeai.gui.toolwindows.components.UserChatPanel;
import com.zhongan.codeai.integrations.llms.LlmProvider;
import com.zhongan.codeai.integrations.llms.LlmProviderFactory;
import com.zhongan.codeai.integrations.llms.entity.CodeAIChatCompletionRequest;
import com.zhongan.codeai.integrations.llms.entity.CodeAIMessage;
import com.zhongan.codeai.util.CodeAIMessageBundle;
import com.zhongan.codeai.util.MarkdownUtil;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.BoxLayout;
import javax.swing.JPanel;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;

public class CodeAIChatToolWindow {
    private final JPanel codeAIChatToolWindowPanel;

    private final UserChatPanel userChatPanel;

    private final ScrollablePanel chatContentPanel;

    private final Project project;

    private LlmProvider llmProvider;

    public CodeAIChatToolWindow(Project project, ToolWindow toolWindow) {
        this.project = project;
        this.codeAIChatToolWindowPanel = new JPanel(new GridBagLayout());
        this.chatContentPanel = new ScrollablePanel();
        this.userChatPanel = new UserChatPanel(this::syncSendAndDisplay, this::stopSending);

        var gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weighty = 1;
        gbc.weightx = 1;
        gbc.gridx = 0;
        gbc.gridy = 0;

        chatContentPanel.setLayout(new BoxLayout(chatContentPanel, BoxLayout.Y_AXIS));
        var scrollPane = new JBScrollPane(chatContentPanel);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setBorder(null);
        scrollPane.setViewportBorder(null);
        codeAIChatToolWindowPanel.add(scrollPane, gbc);

        gbc.weighty = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridy = 1;

        codeAIChatToolWindowPanel.add(userChatPanel, gbc);
    }

    public JPanel getCodeAIChatToolWindowPanel() {
        return codeAIChatToolWindowPanel;
    }

    private void showChatContent(String content, int type) {
        var gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weighty = 0;
        gbc.weightx = 0;
        gbc.gridx = 0;
        gbc.gridy = 0;

        ContentComponent contentPanel = new ContentComponent();

        List<String> blocks = MarkdownUtil.splitBlocks(content);
        for (String block : blocks) {
            if (block.startsWith("```")) {
                contentPanel.add(contentPanel.createCodeComponent(project, block));
            } else {
                contentPanel.add(contentPanel.createTextComponent(block));
            }
        }

        ChatDisplayPanel chatDisplayPanel = new ChatDisplayPanel().setText(contentPanel);

        // 0 - user, 1 - system
        if (type == 0) {
            chatDisplayPanel.setUserLabel();
        } else {
            chatDisplayPanel.setSystemLabel();
        }

        chatContentPanel.add(chatDisplayPanel, gbc);
        chatContentPanel.revalidate();
        chatContentPanel.repaint();

        // scroll to bottom
        chatContentPanel.scrollRectToVisible(chatContentPanel.getVisibleRect());
    }

    private String sendMessage(Project project, String message) {
        var codeAIMessage = new CodeAIMessage();
        // FIXME
        codeAIMessage.setRole("user");
        codeAIMessage.setContent(message);

        var request = new CodeAIChatCompletionRequest();
        request.setMessages(List.of(codeAIMessage));

        llmProvider = new LlmProviderFactory().getLlmProvider(project);
        return llmProvider.chatCompletion(request);
    }

    public void syncSendAndDisplay(String message) {
        syncSendAndDisplay(message, null);
    }

    public void syncSendAndDisplay(String message, Consumer<String> callback) {
        // check if sending
        if (userChatPanel.isSending()) {
            return;
        }

        // set status sending
        userChatPanel.setSending(true);
        userChatPanel.setIconStop();

        // show prompt
        showChatContent(message, 0);

        // show thinking
        showChatContent(CodeAIMessageBundle.get("codeai.thinking.content"), 1);

        // FIXME
        new Thread(() -> {
            String result = sendMessage(this.project, message);
            SwingUtilities.invokeLater(() -> {
                int componentCount = chatContentPanel.getComponentCount();
                Component loading = chatContentPanel.getComponent(componentCount - 1);
                chatContentPanel.remove(loading);

                showChatContent(result, 1);
                userChatPanel.setIconSend();
                userChatPanel.setSending(false);

                if (callback != null) {
                    callback.accept(result);
                }
            });
        }).start();
    }

    private void stopSending() {
        llmProvider.interruptSend();
        userChatPanel.setIconSend();
        userChatPanel.setSending(false);
    }

}