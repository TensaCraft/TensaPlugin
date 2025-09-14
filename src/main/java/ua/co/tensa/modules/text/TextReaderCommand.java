package ua.co.tensa.modules.text;

import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import ua.co.tensa.Message;
import ua.co.tensa.Tensa;
import ua.co.tensa.config.Lang;
import ua.co.tensa.modules.AbstractModule;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class TextReaderCommand implements SimpleCommand {



    @Override
    public void execute(final Invocation invocation) {
        CommandSource source = invocation.source();
        String filename = invocation.alias();

        if (!hasPermission(invocation, filename)) {
            Message.sendLang(source, Lang.no_perms);
            return;
        }
        try {
            for (String line : TextReaderModule.readTxt(filename).split("\r\n")) {
                if (line.contains("[center]")) {
                    line = line.replace("[center]", "");
                    Message.send(source, centerText(line));
                } else {
                    Message.send(source, line);
                }
            }
        } catch (IOException e) {
            ua.co.tensa.Message.error(e.getMessage());
            ua.co.tensa.Message.error("An error occurred while reading the text file!");
        }
    }

    public String centerText(String text){
        int maxWidthPx = 65;
        text = text.trim();
        int length = text.replaceAll("<[^>]*>", "")
                .replaceAll("&[a-f0-9]", "")
                .length();
        int spaces = (maxWidthPx - length) / 2;

        StringBuilder centeredText = new StringBuilder();
        for (int i = 0; i < spaces; i++) {
            centeredText.append(" ");
        }
        centeredText.append(text);
        return centeredText.toString();
    }


    public boolean hasPermission(final Invocation invocation, String arg) {
        return invocation.source().hasPermission("tensa.text." + arg);
    }

    @Override
    public CompletableFuture<List<String>> suggestAsync(final Invocation invocation) {
        return CompletableFuture.completedFuture(List.of(TextReaderModule.getTxtFileNamesWithoutExtension()));
    }

    public static void unregister() {
        CommandManager manager = Tensa.server.getCommandManager();
        for (String cmd : TextReaderModule.getTxtFileNamesWithoutExtension()) {
            manager.unregister(cmd);
        }
    }
}
