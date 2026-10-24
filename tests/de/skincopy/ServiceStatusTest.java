package de.skincopy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class ServiceStatusTest {
    private static void rejected(byte[] bytes) throws Exception {
        try { SkinDownload.validatePng(bytes); } catch(IOException expected) { return; }
        throw new AssertionError("Invalid PNG was accepted");
    }
    public static void main(String[] args) throws Exception {
        if(!ServiceStatus.failure("Profil",401).contains("HTTP 401"))throw new AssertionError();
        if(!ServiceStatus.failure("Upload",403).contains("kein eindeutiger Hinweis"))throw new AssertionError();
        if(!ServiceStatus.failure("Upload",429).contains("warten"))throw new AssertionError();
        rejected(new byte[0]); rejected("<html>denied</html>".getBytes(StandardCharsets.UTF_8));
        rejected(new byte[131073]);
        SkinDownload.validatePng(new byte[]{(byte)137,80,78,71,13,10,26,10});
        System.out.println("PASS: distinct HTTP diagnostics; empty, HTML and oversized image rejection; PNG signature.");
    }
}
