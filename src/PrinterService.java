package FastFood;

import javax.print.*;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet; // Ajouté pour la sécurité
import java.nio.charset.Charset;

public class PrinterService {

    private PrinterService() {}

    private static PrintService findPrinter(String targetName) {
        PrintService[] services = PrintServiceLookup.lookupPrintServices(null, null);
        if (services == null || targetName == null) return null;

        String t = targetName.trim();
        if (t.isEmpty()) return null;

        for (PrintService s : services) {
            if (s != null && s.getName() != null && s.getName().equalsIgnoreCase(t)) {
                return s;
            }
        }

        for (PrintService s : services) {
            if (s != null && s.getName() != null &&
                    s.getName().toUpperCase().contains(t.toUpperCase())) {
                return s;
            }
        }

        return null;
    }

    private static PrintService resolveServiceBySetting(String key) {
        String name = DatabaseHandler.getSetting(key, "");
        if (name == null || name.isBlank()) return null;
        return findPrinter(name);
    }

    private static void printRaw(PrintService service, byte[] data) throws PrintException {
        if (service == null || data == null) return;

        DocFlavor raw = DocFlavor.BYTE_ARRAY.AUTOSENSE;
        DocFlavor flavor = service.isDocFlavorSupported(raw) ? raw : DocFlavor.BYTE_ARRAY.AUTOSENSE;

        DocPrintJob job = service.createPrintJob();
        Doc doc = new SimpleDoc(data, flavor, null);
        job.print(doc, new HashPrintRequestAttributeSet());
    }

    public static void printText(String cleSetting, String texte) {
        try {
            PrintService service = resolveServiceBySetting(cleSetting);
            if (service == null) {
                System.err.println("Imprimante introuvable pour la clé: " + cleSetting);
                return;
            }

            byte[] init = new byte[]{0x1B, 0x40};
            byte[] cp850 = new byte[]{0x1B, 0x74, 0x10};
            byte[] alignL = new byte[]{0x1B, 0x61, 0x00};

            String txt = (texte == null ? "" : texte);
            byte[] body = ("\n" + txt + "\n\n").getBytes(Charset.forName("CP850"));

            byte[] feed = new byte[]{0x1B, 0x64, 0x04};
            byte[] cut = new byte[]{0x1D, 0x56, 0x41, 0x00};

            byte[] data = concat(init, cp850, alignL, body, feed, cut);
            printRaw(service, data);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void openCashDrawer() {
        try {
            PrintService service = resolveServiceBySetting(DatabaseHandler.KEY_PRINTER_CLIENT);
            if (service == null) {
                System.err.println("Imprimante CLIENT introuvable (tiroir caisse).");
                return;
            }

            byte[] init = new byte[]{0x1B, 0x40};
            byte[] pulse = new byte[]{0x1B, 0x70, 0x00, 0x19, (byte) 0xFA};
            byte[] feed = new byte[]{0x0A, 0x0A};

            printRaw(service, concat(init, pulse, feed));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void cutPaper(String cleSetting) {
        try {
            PrintService service = resolveServiceBySetting(cleSetting);
            if (service == null) {
                System.err.println("Imprimante introuvable pour la clé: " + cleSetting);
                return;
            }

            byte[] cut = new byte[]{0x1D, 0x56, 0x41, 0x00};
            printRaw(service, cut);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void debugPrinters() {
        PrintService[] services = PrintServiceLookup.lookupPrintServices(null, null);
        if (services != null) {
            for (PrintService s : services) {
                System.out.println("PRINTER: " + s.getName());
            }
        }
    }

    private static byte[] concat(byte[]... arrays) {
        int len = 0;
        for (byte[] a : arrays) len += (a == null ? 0 : a.length);

        byte[] out = new byte[len];
        int pos = 0;
        for (byte[] a : arrays) {
            if (a == null) continue;
            System.arraycopy(a, 0, out, pos, a.length);
            pos += a.length;
        }
        return out;
    }
}
