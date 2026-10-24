package de.skincopy;

/** Safe diagnostic text, containing no server body, headers or credentials. */
public final class ServiceStatus {
    private ServiceStatus() {}
    public static String failure(String stage, int code) {
        String detail = switch(code) {
            case 401 -> "Minecraft akzeptiert die aktuelle Anmeldung nicht.";
            case 403 -> "Minecraft verweigert den Zugriff. Das ist kein eindeutiger Hinweis auf eine abgelaufene Anmeldung.";
            case 429 -> "Zu viele Anfragen. Bitte etwas warten.";
            default -> "Minecraft-Anfrage fehlgeschlagen.";
        };
        return stage + " HTTP " + code + ": " + detail;
    }
}
