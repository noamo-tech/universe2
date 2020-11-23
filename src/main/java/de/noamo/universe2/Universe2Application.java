package de.noamo.universe2;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.File;
import java.util.Properties;
import java.util.Scanner;

@SpringBootApplication
public class Universe2Application {
    private static final Properties properties = new Properties();
    private static String host = null;
    private static String certificatePath = null;

    public static void main(String[] args) {
        interpretArgs(args);
        importCertificateInJKS();

        // Spring Application starten
        SpringApplication springApplication = new SpringApplication(Universe2Application.class);
        springApplication.setDefaultProperties(properties);
        springApplication.run(args);
    }

    private static void interpretArgs(String[] args) {
        for (String arg : args) {
            if (arg.startsWith("HOST=")) host = arg.substring(5);
            else if (arg.startsWith("CERT=")) certificatePath = arg.substring(5);
        }
    }

    /**
     * Lädt ein Zertifikate aus der Variable {@link Universe2Application#certificatePath} in einen JKS-KeyStore
     */
    private static void importCertificateInJKS() {
        try {
            // Prüft, ob ein Zertifikat verwendet werden soll und ggf. Vorgang beenden
            if (certificatePath == null) return;

            // Prüfen, ob der Host angegeben wurde
            if (host == null) {
                log(2, "Zertifikat konnte nicht geladen werden (HOST wurde in den args nicht mitgegeben). Es wird daher kein Zertifikat verwendet!");
            }

            // Prüfen, ob Datei existiert
            File temp = new File(certificatePath);
            if (!temp.exists() || !temp.isFile()) {
                log(2, "Zertifikat konnte nicht geladen werden (Datei wurde nicht gefunden). Es wird daher kein Zertifikat verwendet!");
                return;
            }

            // Prüfen, ob Datei den dirketen Pfad oder nginx Datei enthält
            if (temp.getName().endsWith(".conf")) {

                // nginx interpretieren (nur Unix)
                certificatePath = null;
                log(0, "Zertifikat-Argument als nginx Konigurationsdatei erkannt");
                Scanner scanner = new Scanner(temp);
                while (scanner.hasNextLine()) {
                    String line = scanner.nextLine();
                    if (line.contains("ssl_certificate")) {
                        line = line.split("/", 2)[1];
                        line = "/" + line.substring(0, line.length() - 1);
                        log(0, "Erkannter Pfad des Zertifikates: " + line);
                        certificatePath = line;
                        break;
                    }
                }
                if (certificatePath == null) {
                    log(2, "\"last_nginx.conf\"-Datei konnte nicht interpretiert werden. Es wird daher kein Zertifikat verwendet!");
                    return;
                }
            }

            // Zielpfad vorbereiten
            String home = Universe2Application.class.getResource("Universe2Application.class").getPath();

            // ggf. alten PKCS12 KeyStore löschen
            File oldPkcs12 = new File(home + "universe.pkcs12");
            if (oldPkcs12.delete()) log(0, "Es wurde eine alter PKCS12 Keystore gefunden & geloescht");

            // PEM-Zertifikat in einen PKCS12-Keystore speichern
            Process p1 = Runtime.getRuntime().exec("openssl pkcs12 " +
                    "-export " +
                    "-in " + certificatePath + " " +
                    "-out " + home + "universe.pkcs12 " +
                    "-passout pass:temppw " +
                    "-name " + host);
            int exitVal1 = p1.waitFor();
            if (exitVal1 != 0) throw new Exception("Error PEM to PKCS12");

            // Properties in SpringBoot einfügen
            properties.setProperty("server.ssl.key-store-type", "PKCS12");
            properties.setProperty("server.ssl.key-store", "classpath:universe.pkcs12");
            properties.setProperty("server.ssl.key-store-password", "temppw");
            properties.setProperty("server.ssl.key-alias", host);

            // Log ausgeben
            log(1, "Zertifikat fuer " + host + " erfolgreich eingelesen");
        } catch (Exception e) {
            log(2, "Zertifikat fuer " + host + "konnte nicht verarbeitet werden (" + e.getMessage() + ")");
        }
    }

    /**
     * Loggt einen Text in der Konsole (mit farbigem Prefix)
     *
     * @param type 0=plain, 1=ok prefix, 2=fehler prefix
     */
    static void log(int type, String message) {
        String pre = (type == 2 ? "[\033[0;31mFEHLER\033[0m] " : (type == 1 ? "[\033[0;32mOK\033[0m] " : ""));
        System.out.println(pre + message);
    }
}
