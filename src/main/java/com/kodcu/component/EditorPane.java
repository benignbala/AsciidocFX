package com.kodcu.component;

import com.kodcu.controller.ApplicationController;
import com.kodcu.other.IOHelper;
import com.kodcu.service.ParserService;
import com.kodcu.service.ThreadService;
import com.kodcu.service.convert.markdown.MarkdownService;
import com.kodcu.service.extension.AsciiTreeGenerator;
import com.kodcu.service.shortcut.ShortcutProvider;
import com.kodcu.service.ui.TabService;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Worker;
import javafx.event.EventHandler;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebEvent;
import javafx.scene.web.WebView;
import javafx.util.Callback;
import netscape.javascript.JSObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Created by usta on 09.04.2015.
 */
@Component
@Scope("prototype")
public class EditorPane extends AnchorPane {

    private final WebView webView;
    private final Logger logger = LoggerFactory.getLogger(EditorPane.class);
    private final ApplicationController controller;
    private final ThreadService threadService;
    private final ShortcutProvider shortcutProvider;
    private final ApplicationContext applicationContext;
    private final TabService tabService;
    private final AsciiTreeGenerator asciiTreeGenerator;
    private final ParserService parserService;
    private final ObservableList<Runnable> handleReadyTasks;
    private String mode = "ace/mode/asciidoc";
    private String initialEditorValue = "";
    private Path path;

    @Autowired
    public EditorPane(ApplicationController controller, ThreadService threadService, ShortcutProvider shortcutProvider, ApplicationContext applicationContext, TabService tabService, AsciiTreeGenerator asciiTreeGenerator, ParserService parserService) {
        this.controller = controller;
        this.threadService = threadService;
        this.shortcutProvider = shortcutProvider;
        this.applicationContext = applicationContext;
        this.tabService = tabService;
        this.asciiTreeGenerator = asciiTreeGenerator;
        this.handleReadyTasks = FXCollections.observableArrayList();
        this.parserService = parserService;
        this.webView = new WebView();
        webEngine().setConfirmHandler(this::handleConfirm);
        initializeMargins();
        initializeEditorContextMenus();
    }

    private Boolean handleConfirm(String param) {
        if ("command:ready".equals(param)) {
            handleEditorReady();
            for (Runnable handleReadyTask : handleReadyTasks) {
                handleReadyTask.run();
            }
            handleReadyTasks.clear();
        }
        return false;
    }

    private void handleEditorReady() {
        getWindow().setMember("afx", controller);
        getWindow().call("updateOptions");

        if (Objects.nonNull(path)) {
            threadService.runTaskLater(() -> {
                final String content = IOHelper.readFile(path);
                threadService.runActionLater(() -> {
                    getWindow().call("changeEditorMode", path.toUri().toString());
                    getWindow().call("setInitialized");
                    getWindow().call("setEditorValue", content);
                });
            });
        } else {
            getWindow().call("setInitialized");
            getWindow().call("setEditorValue", initialEditorValue);
        }
        this.getChildren().add(webView);
        webView.requestFocus();
    }

    private void initializeMargins() {
        AnchorPane.setBottomAnchor(this, 0D);
        AnchorPane.setTopAnchor(this, 0D);
        AnchorPane.setLeftAnchor(this, 0D);
        AnchorPane.setRightAnchor(this, 0D);
        VBox.setVgrow(this, Priority.ALWAYS);
        AnchorPane.setBottomAnchor(webView, 0D);
        AnchorPane.setTopAnchor(webView, 0D);
        AnchorPane.setLeftAnchor(webView, 0D);
        AnchorPane.setRightAnchor(webView, 0D);
        VBox.setVgrow(webView, Priority.ALWAYS);
    }

    public void load(String url) {
        if (Objects.nonNull(url))
            Platform.runLater(() -> {
                webEngine().load(url);
            });
        else
            logger.error("Url is not loaded. Reason: null reference");
    }

    public String getLocation() {
        return webEngine().getLocation();
    }

    public Object call(String methodName, Object... args) {
        return getWindow().call(methodName, args);
    }

    public WebEngine webEngine() {
        return webView.getEngine();
    }

    public WebView getWebView() {
        return webView;
    }

    public void confirmHandler(Callback<String, Boolean> confirmHandler) {
        webEngine().setConfirmHandler(confirmHandler);
    }

    public JSObject getWindow() {
        return (JSObject) webEngine().executeScript("window");
    }

    public String getEditorValue() {
        return (String) webEngine().executeScript("editor.getValue()");
    }

    public void setEditorValue(String value) {
       threadService.runActionLater(()->{
           getWindow().setMember("editorValue", value);
           webEngine().executeScript("editor.setValue(editorValue)");
       });
    }

    public void switchMode(Object... args) {
        this.call("switchMode", args);
    }

    public void rerender(Object... args) {
        this.call("rerender", args);
    }

    public void focus() {
        webView.requestFocus();
    }

    public void moveCursorTo(Integer lineno) {
        if (Objects.nonNull(lineno)) {
            webEngine().executeScript(String.format("editor.gotoLine(%d,3,false)", (lineno)));
            webEngine().executeScript(String.format("editor.scrollToLine(%d,false,false,function(){})", (lineno - 1)));
        }
    }

    public void changeEditorMode(Path path) {
        if (Objects.nonNull(path)) {
            String mode = (String) webEngine().executeScript(String.format("changeEditorMode('%s')", path.toUri().toString()));
            setMode(mode);
        }
    }

    public String editorMode() {
        return (String) this.call("editorMode", new Object[]{});
    }

    public void fillModeList(ObservableList modeList) {
        threadService.runActionLater(() -> {
            this.call("fillModeList", modeList);
        });
    }

    public boolean is(String mode) {
        return ("ace/mode/" + mode).equalsIgnoreCase(this.mode);
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getMode() {
        return mode;
    }

    public void setTheme(String theme) {
        threadService.runActionLater(() -> {
            this.call("changeTheme", theme);
        });
    }

    public void setFontSize(int fontSize) {
        threadService.runActionLater(() -> {
            this.call("changeFontSize", fontSize);
        });
    }

    public void setShowGutter(Boolean showGutter) {
        threadService.runActionLater(() -> {
            this.call("setShowGutter", showGutter);
        });
    }

    public void setUseWrapMode(Boolean useWrapMode) {
        threadService.runActionLater(() -> {
            this.call("setUseWrapMode", useWrapMode);
        });
    }

    public void setWrapLimitRange(Integer wrapLimitRange) {
        threadService.runActionLater(() -> {
            this.call("setWrapLimitRange", wrapLimitRange);
        });
    }


    public void insert(String text) {
        threadService.runActionLater(() -> {
            JSObject editor = (JSObject) webEngine().executeScript("editor");
            editor.call("insert", text);
        });
    }

    public void execCommand(String command) {
        threadService.runActionLater(() -> {
            JSObject editor = (JSObject) webEngine().executeScript("editor");
            editor.call("execCommand", command);
        });
    }

    public String editorSelection() {
        return (String) webEngine().executeScript("editor.session.getTextRange(editor.getSelectionRange())");
    }

    public void initializeEditorContextMenus() {

        ContextMenu menu = new ContextMenu();

        MenuItem copy = MenuItemBuilt.item("Copy").click(event1 -> {
            controller.cutCopy(editorSelection());
        });
        MenuItem paste = MenuItemBuilt.item("Paste").click(e -> {
            controller.paste();
        });
        MenuItem pasteRaw = MenuItemBuilt.item("Paste raw").click(e -> {
            controller.pasteRaw();
        });
        MenuItem indexSelection = MenuItemBuilt.item("Index selection").click(e -> {
            shortcutProvider.getProvider().addIndexSelection();
        });
        MenuItem markdownToAsciidoc = MenuItemBuilt.item("Markdown to Asciidoc").click(e -> {
            MarkdownService markdownService = applicationContext.getBean(MarkdownService.class);
            markdownService.convertToAsciidoc(getEditorValue(),
                    content -> threadService.runActionLater(() -> {
                        tabService.newDoc(content);
                    }));
        });

        getWebView().setOnMouseClicked(event -> {

            if (menu.getItems().size() == 0) {
                menu.getItems().addAll(copy, paste, pasteRaw,
                        markdownToAsciidoc,
                        indexSelection
                );
            }

            if (menu.isShowing()) {
                menu.hide();
            }
            if (event.getButton() == MouseButton.SECONDARY) {
                markdownToAsciidoc.setVisible(isMarkdown());
                indexSelection.setVisible(isAsciidoc());
                menu.show(getWebView(), event.getScreenX(), event.getScreenY());
            }
        });

        getWebView().setOnDragDropped(event -> {
            Dragboard dragboard = event.getDragboard();
            boolean success = false;

            if (dragboard.hasFiles() && !dragboard.hasString()) {

                List<File> dragboardFiles = dragboard.getFiles();

                if (dragboardFiles.size() == 1) {
                    Path path = dragboardFiles.get(0).toPath();
                    if (Files.isDirectory(path)) {

                        threadService.runTaskLater(() -> {
                            StringBuffer buffer = new StringBuffer();
                            buffer.append("[tree,file=\"\"]");
                            buffer.append("\n--\n");
                            buffer.append(asciiTreeGenerator.generate(path));
                            buffer.append("\n--");
                            threadService.runActionLater(() -> {
                                insert(buffer.toString());
                            });
                        });

                        success = true;
                    }
                }

                Optional<String> block = parserService.toImageBlock(dragboardFiles);
                if (block.isPresent()) {
                    insert(block.get());
                    success = true;
                } else {
                    block = parserService.toIncludeBlock(dragboardFiles);
                    if (block.isPresent()) {
                        insert(block.get());
                        success = true;
                    }
                }

            }

            if (dragboard.hasHtml() && !success) {
                Optional<String> block = parserService.toWebImageBlock(dragboard.getHtml());
                if (block.isPresent()) {
                    insert(block.get());
                    success = true;
                }
            }

            if (dragboard.hasString() && !success) {
                insert(dragboard.getString());
                success = true;
            }

            event.setDropCompleted(success);
            event.consume();
        });
    }

    public ObservableList<Runnable> getHandleReadyTasks() {
        return handleReadyTasks;
    }

    public void setInitialEditorValue(String initialEditorValue) {
        this.initialEditorValue = initialEditorValue;
    }

    public boolean isMarkdown() {
        return is("markdown");
    }

    public boolean isAsciidoc() {
        return is("asciidoc");
    }

    public Path getPath() {
        return path;
    }

    public void setPath(Path path) {
        this.path = path;
    }
}
