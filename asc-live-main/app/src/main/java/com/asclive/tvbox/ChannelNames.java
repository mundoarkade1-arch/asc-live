package com.asclive.tvbox;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Entende o NOME de um canal da lista: tira a qualidade (SD, HD, FHD, 4K...) para achar
 * a "chave" do canal, e descobre qual é a qualidade. Não usa Android: roda em qualquer Java.
 *
 * Exemplos (nome da lista -> chave | qualidade):
 *   "Premiere 1 FHD"      -> "premiere 1"  | FHD
 *   "PREMIERE 1 [HD]"     -> "premiere 1"  | HD
 *   "Premiere 1 SD"       -> "premiere 1"  | SD
 *   "Sport TV 2 4K H265"  -> "sportv 2"    | UHD4K
 *   "SporTV 2 SD"         -> "sportv 2"    | SD
 *   "BR: Globo SP FHD"    -> "globo sp"    | FHD
 *   "ESPN 4 ᴴᴰ"           -> "espn 4"      | HD
 *   "Combate (FHD) 50fps" -> "combate"     | FHD
 *   "Telecine Pipoca"     -> "telecine pipoca" | OUTRO
 *   "TNT Sports 1080p"    -> "tnt sports"  | FHD
 */
final class ChannelNames {

    static final String SD = "SD";
    static final String HD = "HD";
    static final String FHD = "FHD";
    static final String UHD4K = "UHD4K";
    static final String OUTRO = "OUTRO";

    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern NOT_ALNUM = Pattern.compile("[^a-z0-9]+");
    /** Prefixo de país no começo ("BR:", "[BR]", "BR |"). */
    private static final Pattern COUNTRY = Pattern.compile(
            "^\\s*[\\[(|]?\\s*(br|brasil|brazil|pt)\\s*[\\])|:\\-]+\\s*");
    private static final Pattern Q_4K = Pattern.compile("\\b(4k|uhd|2160p?)\\b");
    private static final Pattern Q_FHD = Pattern.compile("\\b(fhd|full ?hd|1080p?|1080i)\\b");
    private static final Pattern Q_HD = Pattern.compile("\\b(hd|720p?)\\b");
    private static final Pattern Q_SD = Pattern.compile("\\b(sd|480p?|360p?)\\b");
    private static final Pattern HEVC = Pattern.compile("\\b(h ?265|x ?265|hevc)\\b");
    /** Tudo que é "ruído de qualidade" e sai da chave. */
    private static final Pattern NOISE = Pattern.compile(
            "\\b(uhd|4k|2160p?|fhd|full ?hd|1080p?|1080i|hd|720p?|sd|480p?|360p?"
                    + "|h ?26[45]|x ?26[45]|hevc|[0-9]{2}fps)\\b");
    private static final Pattern SPACES = Pattern.compile("\\s+");
    private static final Pattern DISPLAY_NOISE = Pattern.compile(
            "(?i)[\\[(]?\\b(uhd|4k|2160p?|fhd|full ?hd|1080p?|1080i|hd|720p?|sd|480p?|360p?"
                    + "|h\\.? ?26[45]|x ?26[45]|hevc|[0-9]{2}fps)\\b[\\])]?");

    private ChannelNames() {
    }

    /** Texto simples: sem acento, minúsculas, só letras, números e espaço único. */
    private static String plain(String name) {
        if (name == null) {
            return "";
        }
        String d = Normalizer.normalize(name, Normalizer.Form.NFKD);
        d = MARKS.matcher(d).replaceAll("").toLowerCase(Locale.ROOT);
        d = COUNTRY.matcher(d).replaceFirst("");
        d = NOT_ALNUM.matcher(d).replaceAll(" ");
        return SPACES.matcher(d).replaceAll(" ").trim();
    }

    /** Chave do canal: o mesmo canal em SD, HD, FHD ou 4K dá a MESMA chave. */
    static String key(String name) {
        String p = plain(name);
        p = NOISE.matcher(p).replaceAll(" ");
        p = SPACES.matcher(p).replaceAll(" ").trim();
        // "Sport TV" e "SporTV" são o mesmo canal.
        p = p.replaceAll("\\bsport tv\\b", "sportv");
        if (p.isEmpty()) {
            p = plain(name);
        }
        return p;
    }

    /**
     * Texto que a pessoa digitou (ou palavra da tabela de emissoras), no mesmo formato da chave:
     * sem acento, minúsculas, "sport tv" = "sportv". Serve para procurar dentro de key().
     */
    static String queryKey(String text) {
        return plain(text).replaceAll("\\bsport tv\\b", "sportv");
    }

    /** Qualidade: SD, HD, FHD, UHD4K ou OUTRO (o nome não diz). */
    static String quality(String name) {
        String p = plain(name);
        if (Q_4K.matcher(p).find()) {
            return UHD4K;
        }
        if (Q_FHD.matcher(p).find()) {
            return FHD;
        }
        if (Q_HD.matcher(p).find()) {
            return HD;
        }
        if (Q_SD.matcher(p).find()) {
            return SD;
        }
        return OUTRO;
    }

    /** O nome diz que o vídeo é H265/HEVC (alguns aparelhos, como o TX9, não aguentam). */
    static boolean isHevc(String name) {
        return HEVC.matcher(plain(name)).find();
    }

    /** Ordem de preferência: FHD, HD, (sem qualidade), SD e, por último, 4K. */
    static int rank(String quality) {
        if (FHD.equals(quality)) {
            return 0;
        }
        if (HD.equals(quality)) {
            return 1;
        }
        if (SD.equals(quality)) {
            return 3;
        }
        if (UHD4K.equals(quality)) {
            return 4;
        }
        return 2;
    }

    /** Nome bonito para mostrar na tela (mantém as maiúsculas, tira qualidade e colchetes). */
    static String display(String name) {
        if (name == null) {
            return "";
        }
        String s = Normalizer.normalize(name, Normalizer.Form.NFKC);
        s = COUNTRY.matcher(s.toLowerCase(Locale.ROOT)).find()
                ? s.replaceFirst("(?i)^\\s*[\\[(|]?\\s*(br|brasil|brazil|pt)\\s*[\\])|:\\-]+\\s*", "")
                : s;
        s = DISPLAY_NOISE.matcher(s).replaceAll(" ");
        s = s.replaceAll("[\\[\\]]", " ");
        s = SPACES.matcher(s).replaceAll(" ").trim();
        s = s.replaceAll("[\\s\\-|:]+$", "").trim();
        return s.isEmpty() ? name.trim() : s;
    }

    /** Teste simples: java ChannelNames (imprime os exemplos). */
    public static void main(String[] args) {
        String[] names = {
                "Premiere 1 FHD", "PREMIERE 1 [HD]", "Premiere 1 SD", "Sport TV 2 4K H265",
                "SporTV 2 SD", "BR: Globo SP FHD", "ESPN 4 \u1D34\u1D30", "Combate (FHD) 50fps",
                "Telecine Pipoca", "TNT Sports 1080p", "Canal Brasil HD", "SBT H265 FHD"
        };
        for (String n : names) {
            System.out.println(n + "  ->  key=[" + key(n) + "]  q=" + quality(n)
                    + "  hevc=" + isHevc(n) + "  show=[" + display(n) + "]");
        }
    }
}
