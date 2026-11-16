import java.io.File;
import java.net.URL;
import java.util.Enumeration;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.XMLFileSystem;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;

/**
 * Verificacion offline del tema Soft Dark:
 *  1. Monta layer.xml con el XMLFileSystem real de NetBeans (valida DTD y rutas).
 *  2. Valida cada XML de colores contra el DTD EditorFontsColors oficial.
 */
public final class VerifyTheme {

    public static void main(String[] args) throws Exception {
        String resDir = args[0];              // .../src/main/resources/org/deepsetk/softdark
        String dtdDir = args[1];              // directorio con EditorFontsColors-1_*.dtd
        String profile = args.length > 2 ? args[2] : "SoftDark";

        File layer = new File(resDir, "layer.xml");
        XMLFileSystem fs = new XMLFileSystem(layer.toURI().toURL());

        System.out.println("== Layer montado ==");
        FileObject top = fs.getRoot();
        for (FileObject f : top.getChildren()) {
            System.out.println("  /" + f.getNameExt());
        }
        FileObject fontsColors = fs.findResource("Editors/FontsColors/" + profile + "/Defaults");
        if (fontsColors == null) {
            System.out.println("FALLO: no existe Editors/FontsColors/" + profile + "/Defaults");
            System.exit(1);
        }
        System.out.println("== Defaults del perfil " + profile + " ==");
        for (FileObject f : fontsColors.getChildren()) {
            System.out.println("  " + f.getNameExt()
                    + "  tipo=" + f.getAttribute("nbeditor-settings-ColoringType")
                    + "  size=" + f.getSize());
        }
        System.out.println("== Perfiles de lenguaje ==");
        int langCount = 0;
        for (String mime : new String[]{"text/x-java", "text/xml", "text/x-json", "text/x-yaml",
                                        "text/x-properties", "text/x-sql", "text/x-markdown", "text/html"}) {
            FileObject dir = fs.findResource("Editors/" + mime + "/FontsColors/" + profile + "/Defaults");
            System.out.println("  " + mime + " -> " + (dir == null ? "FALTA" : dir.getChildren().length + " fichero(s)"));
            if (dir != null) {
                langCount++;
            }
        }
        String action = "Actions/Tools/org-deepsetk-"
                + ("Cyberpunk".equals(profile) ? "cyberpunk-apply" : "softdark-apply") + ".instance";
        System.out.println("Accion de menu: "
                + (fs.findResource(action) != null ? "OK" : "FALTA"));

        System.out.println("== Validacion DTD de los XML de colores ==");
        File[] xmls = new File(resDir, "fontscolors").listFiles((d, n) -> n.endsWith(".xml"));
        int errors = 0;
        for (File f : xmls) {
            try {
                parse(f.toURI().toURL(), dtdDir);
                System.out.println("  OK   " + f.getName());
            } catch (Exception ex) {
                errors++;
                System.out.println("  FALLO " + f.getName() + ": " + ex.getMessage());
            }
        }
        System.out.println("Perfiles de lenguaje correctos: " + langCount + "/8");
        System.out.println("Ficheros con error: " + errors);
        if (errors > 0 || langCount < 8) {
            System.exit(1);
        }
        System.out.println("VERIFICACION OK");
    }

    private static void parse(URL url, String dtdDir) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setValidating(true);
        dbf.setNamespaceAware(false);
        dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, false);
        DocumentBuilder db = dbf.newDocumentBuilder();
        db.setEntityResolver(new EntityResolver() {
            @Override
            public InputSource resolveEntity(String publicId, String systemId) {
                if (publicId != null && publicId.contains("Editor Fonts and Colors")) {
                    String dtd = publicId.contains("1.2") ? "EditorFontsColors-1_2.dtd" : "EditorFontsColors-1_1.dtd";
                    File f = new File(dtdDir, dtd);
                    if (f.isFile()) {
                        return new InputSource(f.toURI().toString());
                    }
                }
                return null;
            }
        });
        db.setErrorHandler(new org.xml.sax.ErrorHandler() {
            @Override public void warning(org.xml.sax.SAXParseException e) { }
            @Override public void error(org.xml.sax.SAXParseException e) throws org.xml.sax.SAXException {
                throw e;
            }
            @Override public void fatalError(org.xml.sax.SAXParseException e) throws org.xml.sax.SAXException {
                throw e;
            }
        });
        db.parse(url.toExternalForm());
    }
}
