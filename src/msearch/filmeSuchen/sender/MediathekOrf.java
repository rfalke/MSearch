/*
 * MediathekView
 * Copyright (C) 2008 W. Xaver
 * W.Xaver[at]googlemail.com
 *
 * http://zdfmediathk.sourceforge.net/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package msearch.filmeSuchen.sender;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import msearch.daten.DatenFilm;
import msearch.daten.MSearchConfig;
import msearch.filmeSuchen.MSearchFilmeSuchen;
import static msearch.filmeSuchen.sender.MediathekReader.listeSort;
import msearch.io.MSearchGetUrl;
import msearch.tool.MSearchConst;
import msearch.tool.MSearchLog;
import msearch.tool.MSearchStringBuilder;
import org.apache.commons.lang3.StringEscapeUtils;

public class MediathekOrf extends MediathekReader implements Runnable {

    public static final String SENDER = "ORF";
    private static final String THEMA_TAG = "-1";

    /**
     *
     * @param ssearch
     * @param startPrio
     */
    public MediathekOrf(MSearchFilmeSuchen ssearch, int startPrio) {
        super(ssearch, /* name */ SENDER, /* threads */ 2, /* urlWarten */ 500, startPrio);
    }

    @Override
    void addToList() {
        MSearchStringBuilder seite = new MSearchStringBuilder(MSearchConst.STRING_BUFFER_START_BUFFER);
        listeThemen.clear();
        meldungStart();
        bearbeiteAdresseThemen(seite);
        listeSort(listeThemen, 1);
        int maxTage = MSearchConfig.senderAllesLaden ? 9 : 3;
        for (int i = 0; i < maxTage; ++i) {
            String vorTagen = getGestern(i).toLowerCase();
            bearbeiteAdresseTag("http://tvthek.orf.at/schedule/" + vorTagen, seite);
        }
        if (MSearchConfig.getStop()) {
            meldungThreadUndFertig();
        } else if (listeThemen.size() == 0) {
            meldungThreadUndFertig();
        } else {
            meldungAddMax(listeThemen.size());
            for (int t = 0; t < maxThreadLaufen; ++t) {
                //new Thread(new ThemaLaden()).start();
                Thread th = new Thread(new ThemaLaden());
                th.setName(nameSenderMReader + t);
                th.start();
            }
        }
    }

    private void bearbeiteAdresseTag(String adresse, MSearchStringBuilder seite) {
        // <a href="http://tvthek.orf.at/program/Kultur-heute/3078759/Kultur-Heute/7152535" class="item_inner clearfix">
        seite = getUrlIo.getUri(nameSenderMReader, adresse, MSearchConst.KODIERUNG_UTF, 2, seite, "");
        ArrayList<String> al = new ArrayList<>();
        seite.extractList("<a href=\"http://tvthek.orf.at/program/", "\"", 0, "http://tvthek.orf.at/program/", al);
        for (String s : al) {
            String[] add = new String[]{s, THEMA_TAG}; // werden extra behandelt
            if (!istInListe(listeThemen, add[0], 0)) {
                listeThemen.add(add);
            }
        }
    }

    private void bearbeiteAdresseThemen(MSearchStringBuilder seite) {
        // <a class="base_list_item_inner icon_align" href="http://tvthek.orf.at/programs/genre/Parlament/3309521">     
        seite = getUrlIo.getUri(nameSenderMReader, "http://tvthek.orf.at/programs", MSearchConst.KODIERUNG_UTF, 3, seite, "");
        ArrayList<String> al = new ArrayList<>();
        String thema;
        try {
            seite.extractList("<a class=\"base_list_item_inner icon_align\" href=\"http://tvthek.orf.at/programs/genre/", "\"", 0, "", al);
            for (String s : al) {
                thema = "";
                if (s.contains("/")) {
                    thema = s.substring(0, s.indexOf("/"));
                    if (thema.isEmpty()) {
                        thema = nameSenderMReader;
                    }
                }
                String[] add = new String[]{"http://tvthek.orf.at/programs/genre/" + s, thema};
                if (!istInListe(listeThemen, add[0], 0)) {
                    listeThemen.add(add);
                }
            }
        } catch (Exception ex) {
            MSearchLog.fehlerMeldung(-826341789, MSearchLog.FEHLER_ART_MREADER, "MediathekOrf.bearbeiteAdresseKey", ex);
        }
    }

//    private void bearbeiteAdresseKey(MSearchStringBuilder seite) {
//        // <a class="base_list_item_inner" href="http://tvthek.orf.at/programs/letter/R">
//        seite = getUrlIo.getUri(nameSenderMReader, "http://tvthek.orf.at/programs", MSearchConst.KODIERUNG_UTF, 3, seite, "");
//        ArrayList<String> al = new ArrayList<>();
//        try {
//            seite.extractList("href=\"http://tvthek.orf.at/programs/letter/", "\"", 0, "http://tvthek.orf.at/programs/letter/", al);
//            for (String s : al) {
//                String[] add = new String[]{s, ""};
//                if (!istInListe(listeThemen, add[0], 0)) {
//                    listeThemen.add(add);
//                }
//            }
//        } catch (Exception ex) {
//            MSearchLog.fehlerMeldung(-826341789, MSearchLog.FEHLER_ART_MREADER, "MediathekOrf.bearbeiteAdresseKey", ex);
//        }
//    }
    private class ThemaLaden implements Runnable {

        MSearchGetUrl getUrl = new MSearchGetUrl(wartenSeiteLaden);
        private MSearchStringBuilder seite1 = new MSearchStringBuilder(MSearchConst.STRING_BUFFER_START_BUFFER);
        private MSearchStringBuilder seite2 = new MSearchStringBuilder(MSearchConst.STRING_BUFFER_START_BUFFER);
        private ArrayList<String> al = new ArrayList<>();

        @Override
        public synchronized void run() {
            try {
                meldungAddThread();
                String[] link;
                while (!MSearchConfig.getStop() && (link = listeThemen.getListeThemen()) != null) {
                    try {
                        meldungProgress(link[0]);
                        if (link[1].equals(THEMA_TAG)) {
                            // dann ist von "Tage zurück"
                            feedEinerSeiteSuchen(link[0], THEMA_TAG);
                        } else {
                            sendungen(link[0] /* url */, link[1]);
                        }
                    } catch (Exception ex) {
                        MSearchLog.fehlerMeldung(-795633581, MSearchLog.FEHLER_ART_MREADER, "MediathekOrf.OrfThemaLaden.run", ex);
                    }
                }
            } catch (Exception ex) {
                MSearchLog.fehlerMeldung(-554012398, MSearchLog.FEHLER_ART_MREADER, "MediathekOrf.OrfThemaLaden.run", ex);
            }
            meldungThreadUndFertig();
        }

        private void sendungen(String url, String thema) {
            // <a class="base_list_item_inner" href="http://tvthek.orf.at/programs/letter/R">
            // <a href="http://tvthek.orf.at/program/ZIB-13/71280/ZIB-13/7151714" class="base_list_item_inner icon_align">
            seite1 = getUrlIo.getUri(nameSenderMReader, url, MSearchConst.KODIERUNG_UTF, 2, seite1, "");
            int start = seite1.indexOf("<h3 class=\"subheadline\">Verfügbare Sendungen</h3>");
            int stop;
            int pos, pos1, pos2, count;
            int maxCount = 3;
            String urlFeed;
            if (start > 0) {
                pos = start;
                while (!MSearchConfig.getStop() && (pos = seite1.indexOf("<h4 class=\"base_list_item_headline\">", pos)) != -1) {
                    // über alle Sendungen eines Buchstabens
                    count = 0;
                    pos += "<h4 class=\"base_list_item_headline\">".length();
                    stop = seite1.indexOf("<!-- ende latest:", pos);
                    pos1 = pos;
                    while (!MSearchConfig.getStop() && (pos1 = seite1.indexOf("<a href=\"http://tvthek.orf.at/program/", pos1)) != -1) {
                        // über die eine jeweilige Sendung
                        if (!MSearchConfig.senderAllesLaden) {
                            if (count > maxCount) {
                                break;
                            }
                        }
                        if (pos1 >= stop) {
                            break;
                        }
                        ++count;
                        pos1 += "<a href=\"http://tvthek.orf.at/program/".length();
                        if ((pos2 = seite1.indexOf("\"", pos1)) != -1) {
                            urlFeed = seite1.substring(pos1, pos2);
                            if (urlFeed.isEmpty()) {
                                MSearchLog.fehlerMeldung(-915263654, MSearchLog.FEHLER_ART_MREADER, "MediathekOrf.sendungen", "keine Url: " + url);
                            } else {
                                feedEinerSeiteSuchen("http://tvthek.orf.at/program/" + urlFeed, thema);
                            }
                        }

                    }
                }
            }
        }

        private void feedEinerSeiteSuchen(String strUrlFeed, String thema) {
            //<title> ORF TVthek: a.viso - 28.11.2010 09:05 Uhr</title>
            boolean nurUrlPruefen = false;
            if (thema.equals(THEMA_TAG)) {
                nurUrlPruefen = true;
                thema = nameSenderMReader;
            }
            seite2 = getUrl.getUri_Utf(nameSenderMReader, strUrlFeed, seite2, "");
            String datum = "";
            String zeit = "";
            long duration = 0;
            String description = "";
            String thumbnail = "";
            String tmp;
            String urlRtmpKlein = "", urlRtmp = "", url = "", urlKlein = "";
            String titel = "";
            int tmpPos1, tmpPos2;
            int posStart, posStopAlles, posStopEpisode, pos = 0;
            meldung(strUrlFeed);
            thumbnail = seite2.extract("<meta property=\"og:image\" content=\"", "\"");
            thumbnail = thumbnail.replace("&amp;", "&");
            titel = seite2.extract("<title>", "vom"); //<title>ABC Bär vom 17.11.2013 um 07.35 Uhr / ORF TVthek</title>
            if (titel.isEmpty()) {
                titel = nameSenderMReader;
            }
            datum = seite2.extract("<span class=\"meta meta_date\">", "<");
            if (datum.contains(",")) {
                datum = datum.substring(datum.indexOf(",") + 1).trim();
            }
            zeit = seite2.extract("<span class=\"meta meta_time\">", "<");
            zeit = zeit.replace("Uhr", "").trim();
            if (zeit.length() == 5) {
                zeit = zeit.replace(".", ":") + ":00";
            }
            if ((posStart = seite2.indexOf("\"is_one_segment_episode\":false")) == -1) {
                if ((posStart = seite2.indexOf("\"is_one_segment_episode\":true")) == -1) {
                    MSearchLog.fehlerMeldung(-989532147, MSearchLog.FEHLER_ART_MREADER, "MediathekOrf.feedEinerSeiteSuchen", "keine Url: " + strUrlFeed);
                    return;
                }
            }
            if ((posStopAlles = seite2.indexOf("</script>", posStart)) != -1) {
                // =====================================================
                // mehrer Episoden
                thema = titel;
                while ((pos = seite2.indexOf("\"clickcounter_corrected\":\"0\"", pos)) != -1) {
                    posStopEpisode = seite2.indexOf("\"is_episode_one_segment_episode\":false", pos);
                    if (posStopEpisode == -1) {
                        posStopEpisode = seite2.indexOf("\"is_episode_one_segment_episode\":true", pos);
                    }
                    if (posStopEpisode == -1 || posStopEpisode > posStopAlles) {
                        break;
                    }
                    if (pos > posStopAlles) {
                        break;
                    }
                    pos += "\"clickcounter_corrected\":\"0\"".length();
                    tmp = seite2.extract("\"duration\":\"", "\"", pos, posStopEpisode);
                    try {
                        duration = Long.parseLong(tmp) / 1000; // time in milliseconds
                    } catch (Exception ex) {
                    }
                    titel = seite2.extract("\"header\":\"", "\",", pos, posStopEpisode);//"header":"Lehrerdienstrecht beschlossen"
//                        titel = GuiFunktionen.utf8(titel);
                    if (!titel.equals(StringEscapeUtils.unescapeJava(titel))) {
                        titel = StringEscapeUtils.unescapeJava(titel).trim();
                    }

                    description = seite2.extract("\"description\":\"", "\"", pos, posStopEpisode);
                    if (!description.equals(StringEscapeUtils.unescapeJava(description))) {
                        description = StringEscapeUtils.unescapeJava(description).trim();
                    }
                    // =======================================================
                    // url
                    url = "";
                    urlKlein = "";
                    tmpPos1 = pos;
                    final String MUSTER_URL = "quality\":\"Q6A\",\"quality_string\":\"hoch\",\"src\":\"http";
                    while ((tmpPos1 = seite2.indexOf(MUSTER_URL, tmpPos1)) != -1) {
                        tmpPos1 += MUSTER_URL.length();
                        if (tmpPos1 > posStopEpisode) {
                            break;
                        }
                        if ((tmpPos2 = seite2.indexOf("\"", tmpPos1)) != -1) {
                            url = seite2.substring(tmpPos1, tmpPos2);
                            if (url.endsWith(".mp4")) {
                                break;
                            }
                        }
                    }
                    if (!url.isEmpty()) {
                        url = url.replace("\\/", "/");
                        url = "http" + url;
                    } else {
                        url = seite2.extract("quality\":\"Q6A\",\"quality_string\":\"hoch\",\"src\":\"rtmp", "\"", posStopEpisode);
                        url = url.replace("\\/", "/");
                        if (!url.isEmpty()) {
                            url = "rtmp" + url;
                            int mpos = url.indexOf("mp4:");
                            if (mpos != -1) {
                                urlRtmp = "-r " + url + " -y " + url.substring(mpos) + " --flashVer WIN11,4,402,265 --swfUrl http://tvthek.orf.at/flash/player/TVThekPlayer_9_ver18_1.swf";
                            }
                        }
                    }
                    // =======================================================
                    // urlKlein
                    tmpPos1 = pos;
                    final String MUSTER_URL_KLEIN = "quality\":\"Q4A\",\"quality_string\":\"mittel\",\"src\":\"http";
                    while ((tmpPos1 = seite2.indexOf(MUSTER_URL_KLEIN, tmpPos1)) != -1) {
                        tmpPos1 += MUSTER_URL_KLEIN.length();
                        if (tmpPos1 > posStopEpisode) {
                            break;
                        }
                        if ((tmpPos2 = seite2.indexOf("\"", tmpPos1)) != -1) {
                            urlKlein = seite2.substring(tmpPos1, tmpPos2);
                            if (urlKlein.endsWith(".mp4")) {
                                break;
                            }
                        }
                    }
                    if (!urlKlein.isEmpty()) {
                        urlKlein = urlKlein.replace("\\/", "/");
                        urlKlein = "http" + urlKlein;
                    } else {
                        urlKlein = seite2.extract("quality\":\"Q4A\",\"quality_string\":\"mittel\",\"src\":\"rtmp", "\"", pos, posStopEpisode);
                        urlKlein = urlKlein.replace("\\/", "/");
                        if (!urlKlein.isEmpty()) {
                            urlKlein = "rtmp" + urlKlein;
                            int mpos = urlKlein.indexOf("mp4:");
                            if (mpos != -1) {
                                urlRtmpKlein = "-r " + urlKlein + " -y " + urlKlein.substring(mpos) + " --flashVer WIN11,4,402,265 --swfUrl http://tvthek.orf.at/flash/player/TVThekPlayer_9_ver18_1.swf";
                            }
                        }
                    }
                    if (!url.isEmpty()) {
                        DatenFilm film = new DatenFilm(nameSenderMReader, thema, strUrlFeed, titel, url, urlRtmp, datum, zeit, duration, description,
                                thumbnail, new String[]{});
                        if (!urlKlein.isEmpty()) {
                            film.addUrlKlein(urlKlein, urlRtmpKlein);
                        }
                        addFilm(film, nurUrlPruefen);
                    } else {
                        MSearchLog.fehlerMeldung(-989532147, MSearchLog.FEHLER_ART_MREADER, "MediathekOrf.feedEinerSeiteSuchen", "keine Url: " + strUrlFeed);
                    }
                }
            }
        }
    }

//    void addFilm(DatenFilm film, boolean nurUrlPruefen) {
//        // http://apasfpd.apa.at/cms-worldwide/online/30e56502951a39b3dcc07cbd42b93dd8/1386353983/2013-12-06_1830_tl_02_heute-konkret_-3--Beschwerden__7210188__o__0000942829__s7210191___en_ORF2HiRes_18322601P_18362214P_Q6A.mp4
//        // http://apasfpd.apa.at/cms-worldwide/online/3b0619925919951eede27b73836e05f7/1386353969/2013-12-06_1830_tl_02_heute-konkret_-3--Beschwerden__7210188__o__0000942829__s7210191___en_ORF2HiRes_18322601P_18362214P_Q6A.mp4
//        // die 2 Nummer nach "/online/" werden nicht verglichen
//        if (mSearchFilmeSuchen.listeFilmeNeu.filmExists_Orf(film)) {
//            return;
//        }
//        super.addFilm(film, nurUrlPruefen);
//    }
    public static String getGestern(int tage) {
        try {
            //SimpleDateFormat sdfOut = new SimpleDateFormat("EEEE", Locale.US);
            SimpleDateFormat sdfOut = new SimpleDateFormat("dd.MM.yyyy");
            return sdfOut.format(new Date(new Date().getTime() - tage * (1000 * 60 * 60 * 24)));
        } catch (Exception ex) {
            return "";
        }
    }
}
