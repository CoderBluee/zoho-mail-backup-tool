package com.pstconverter;

public class Launcher {

    public static void main(String[] args) {

        String configPath = null;
        for (int i = 0; i < args.length; i++) {
            if (("--config".equals(args[i]) || "-c".equals(args[i])) && (i + 1 < args.length)) {
                configPath = args[i + 1];
                break;
            }
        }
        if (configPath != null) {
            com.pstconverter.controller.CommandLineController.run(configPath);
            System.exit(0);
        } else {
            App.main(args);
        }
    }
}
