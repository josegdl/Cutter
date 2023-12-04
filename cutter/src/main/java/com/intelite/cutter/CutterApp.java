/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.intelite.cutter;

import com.intelite.threads.CorteMp3;
import com.intelite.threads.CorteMp4;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import javax.persistence.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Hector
 */
public class CutterApp {

    private static final Logger LOG = LoggerFactory.getLogger(CutterApp.class);
    public static final DateFormat DF = new SimpleDateFormat("dd/MM/yyyy-HH:mm:ss: ");
    public static final DateFormat DF_YY = new SimpleDateFormat("yy");
    public static final DateFormat DF_YYYY = new SimpleDateFormat("yyyy");
    public static final DateFormat DF_MM = new SimpleDateFormat("MM");
    public static final DateFormat DF_MMMM = new SimpleDateFormat("MMMM", new Locale("es", "ES"));
    public static final DateFormat DF_DD = new SimpleDateFormat("dd");

    public static final String DS = System.getProperty("file.separator").equals("\\") ? "\\" : "/";
    public static final String DIR_TMP = "TEMP" + DS;
    public static final String CONFIG_FILE = System.getProperty("user.dir") + DS + "config.properties";
    public static final String FFMPEG_PATH = System.getProperty("user.dir") + DS + "ffmpeg";
    public static final String FFPROBE_PATH = System.getProperty("user.dir") + DS + "ffprobe";
    public static final String ASFBIN_PATH = System.getProperty("user.dir") + DS + "asfbin.exe";
    private static final Boolean ISLINUX = DS.equals("/");
    public static final String ERROR = "ERROR! > ";
    public static boolean brun = false;

    public static final String NAME_PU = "cutter_pu";
    public static final Map<String, String> PROPS_PU = new HashMap<>(1);
//    private static final String JDBC_URL_KEY = "javax.persistence.jdbc.url";
    private static final String JDBC_URL_KEY = "hibernate.connection.url";
    private static final String DB_PORT = ":1521";
    private static final String DB_SID = ":intelica";
    private static String jdbc_url_val = "jdbc:oracle:thin:@";

    private static String db_host;
    public static String montaje;
    public static String carpeta;
    private static Integer hilos_mp3;
    private static Integer hilos_mp4a;
    private static Integer hilos_mp4b;
    private static Integer hilos_mp4c;
    private static Integer hilos_mp4d;
    public static Integer origen;

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
//        LOG.error("===== CUTTER v.20211124 =====\n");
        LOG.error("===== CUTTER v.20220505 =====\n"); // Se indica el codec de audio en los cortes mp4>mp4
        if (createDirTemp()) {
            if (loadConfig()) {
                updateStatusCortes();
                iniCorte();
            }
        }
    }

    private static boolean createDirTemp() {
        File dir_tmp = new File(DIR_TMP);
        if (dir_tmp.exists()) {
            // Se borra el contenido de la carpeta temporal
            String[] files = dir_tmp.list();
            for (String file : files) {
                File f = new File(dir_tmp.getPath(), file);
                f.delete();
            }
            return true;
        } else {
            System.out.println(DF.format(new Date()) + "Creando carpeta " + dir_tmp.getAbsolutePath() + "...");
            // Se crea la carpeta temporal
            if (ISLINUX) {
//                Set<PosixFilePermission> perms = PosixFilePermissions.fromString("rwxrwxrwx"); // 777
                Set<PosixFilePermission> perms = PosixFilePermissions.fromString("rwxr-xr-x"); // 755
                try {
                    Files.createDirectories(Paths.get(dir_tmp.getAbsolutePath()), PosixFilePermissions.asFileAttribute(perms));
                    return true;
                } catch (IOException e) {
                    LOG.error(ERROR + "NO SE PUDO CREAR LA CARPETA -> " + dir_tmp.getAbsolutePath(), e);
                }
            } else {
                if (dir_tmp.mkdirs()) {
                    return true;
                } else {
                    LOG.error(ERROR + "NO SE PUDO CREAR LA CARPETA -> " + dir_tmp.getAbsolutePath());
                }
            }
        }
        return false;
    }

    private static boolean loadConfig() {
        System.out.println(DF.format(new Date()) + "Cargando configuración...");
        Properties p = new Properties();
        File file = new File(CONFIG_FILE);
        if (!file.exists()) {
            // Se crea el archivo config.properties
            try {
                p.setProperty("DB_HOST", "99.90.100.14");
                p.setProperty("MONTAJE", "documentos16");
                p.setProperty("CARPETA", "Imagenes_");
                p.setProperty("HILOS_MP3", "0");
                p.setProperty("HILOS_MP4A", "0");
                p.setProperty("HILOS_MP4B", "0");
                p.setProperty("HILOS_MP4C", "0");
                p.setProperty("HILOS_MP4D", "0");
                p.setProperty("ORIGEN", "2");
                p.store(new FileWriter(CONFIG_FILE), "========== CONFIGURACION CUTTER ==========\n");

            } catch (IOException e) {
                LOG.error(ERROR + "No se pudo crear el archivo config.properties", e);
            }
        }
        try {
            p.load(new FileReader(CONFIG_FILE));
            db_host = p.getProperty("DB_HOST", "");
            montaje = p.getProperty("MONTAJE", "");
            carpeta = p.getProperty("CARPETA", "");
            hilos_mp3 = Integer.parseInt(p.getProperty("HILOS_MP3", "0"));
            hilos_mp4a = Integer.parseInt(p.getProperty("HILOS_MP4A", "0"));
            hilos_mp4b = Integer.parseInt(p.getProperty("HILOS_MP4B", "0"));
            hilos_mp4c = Integer.parseInt(p.getProperty("HILOS_MP4C", "0"));
            hilos_mp4d = Integer.parseInt(p.getProperty("HILOS_MP4D", "0"));
            origen = Integer.parseInt(p.getProperty("ORIGEN", "0"));

            if (!db_host.equals("") && !montaje.equals("") && !carpeta.equals("") && !origen.equals(0)) {
                montaje = setBackslash(montaje);
                LOG.error("Base de Datos: " + db_host);
                LOG.error("Montaje Destino: " + montaje);
                LOG.error("Carpeta Destino: " + carpeta);
                LOG.error("Origen: " + (origen.equals(1) ? "1 (Toluca)" : "2 (Dante)") + "\n");
                // jdbc:oracle:thin:@99.90.100.14:1521:intelica
                jdbc_url_val += db_host + DB_PORT + DB_SID;
                PROPS_PU.put(JDBC_URL_KEY, jdbc_url_val);
                return true;
            } else {
                if (db_host.equals("")) {
                    LOG.error(ERROR + "Archivo de configuración -> Variable DB_HOST no definida o vacía");
                }
                if (montaje.equals("")) {
                    LOG.error(ERROR + "Archivo de configuración -> Variable MONTAJE no está definida o está vacía");
                }
                if (carpeta.equals("")) {
                    LOG.error(ERROR + "Archivo de configuración -> Variable CARPETA no está definida o está vacía");
                }
                if (origen.equals(0)) {
                    LOG.error(ERROR + "Archivo de configuración -> Variable ORIGEN no está definida o está vacía");
                }
            }

        } catch (FileNotFoundException e) {
            LOG.error(ERROR + "No se encontró el archivo config.properties", e);
        } catch (IOException e) {
            LOG.error(ERROR + "En el archivo config.properties", e);
        }
        return false;
    }

    private static String setBackslash(String cadena) {
        if (cadena != null && !cadena.equals("")) {
            int length = cadena.length();
            String backslash = cadena.substring(length - 1);
            if (!backslash.equals(DS)) {
                cadena += DS;
            }
        }
        return cadena;
    }

    private static void updateStatusCortes() {
        EntityManagerFactory factory = Persistence.createEntityManagerFactory(NAME_PU, PROPS_PU);
//        System.out.println(FACTORY.getProperties().get("javax.persistence.jdbc.url"));
        EntityManager em = factory.createEntityManager();
        try {
            em.getTransaction().begin();
            if (hilos_mp3 > 0) {
                Query query = em.createQuery("UPDATE Corte SET status = 0 WHERE status = 1 AND tipo_origen = 3 AND origen = " + origen);
                query.executeUpdate();
            }
            if (hilos_mp4a > 0) {
                Query query = em.createQuery("UPDATE Corte SET status = 0 WHERE status = 1 AND tipo_origen = 1 AND origen = " + origen);
                query.executeUpdate();
            }
            if (hilos_mp4b > 0) {
                Query query = em.createQuery("UPDATE Corte SET status = 0 WHERE status = 1 AND tipo_origen = 2 AND origen = " + origen);
                query.executeUpdate();
            }
            if (hilos_mp4c > 0) {
                Query query = em.createQuery("UPDATE Corte SET status = 0 WHERE status = 1 AND tipo_origen = 5 AND origen = " + origen);
                query.executeUpdate();
            }
            if (hilos_mp4d > 0) {
                Query query = em.createQuery("UPDATE Corte SET status = 0 WHERE status = 1 AND tipo_origen = 6 AND origen = " + origen);
                query.executeUpdate();
            }
            em.getTransaction().commit();

        } catch (Exception e) {
            LOG.error(ERROR + "Update Status Cortes", e);
        } finally {
            em.close();
            factory.close();
        }
    }

    private static void iniCorte() {
        System.out.println(DF.format(new Date()) + "Creando hilos -> Inicia proceso de corte...");
        brun = true;
        if (hilos_mp3 > 0) {
            hilos_mp3 += 1;
            for (int i = 1; i < hilos_mp3; i++) {
                CorteMp3 corte_mp3 = new CorteMp3("MP3-" + i + ">");
                corte_mp3.start();
                try {
                    Thread.sleep((hilos_mp3 - i) * (1000 + (i * 100)));
                } catch (InterruptedException e) {
                    LOG.error(ERROR + "iniCorte() -> Sleep crear hilos mp3: ", e);
                }
            }
        }

        if (hilos_mp4a > 0) {
            hilos_mp4a += 1;
            for (int i = 1; i < hilos_mp4a; i++) {
                CorteMp4 corte_mp4 = new CorteMp4("MP4a-" + i + ">", "1");
                corte_mp4.start();
                try {
                    Thread.sleep((hilos_mp4a - i) * (1000 + (i * 100)));
                } catch (InterruptedException e) {
                    LOG.error(ERROR + "iniCorte() -> Sleep crear hilos mp4a: ", e);
                }
            }
        }
        if (hilos_mp4b > 0) {
            hilos_mp4b += 1;
            for (int i = 1; i < hilos_mp4b; i++) {
                CorteMp4 corte_mp4 = new CorteMp4("MP4b-" + i + ">", "2");
                corte_mp4.start();
                try {
                    Thread.sleep((hilos_mp4b - i) * (1000 + (i * 100)));
                } catch (InterruptedException e) {
                    LOG.error(ERROR + "iniCorte() -> Sleep crear hilos mp4b: ", e);
                }
            }
        }
        if (hilos_mp4c > 0) {
            hilos_mp4c += 1;
            for (int i = 1; i < hilos_mp4c; i++) {
                CorteMp4 corte_mp4 = new CorteMp4("MP4c-" + i + ">", "5");
                corte_mp4.start();
                try {
                    Thread.sleep((hilos_mp4c - i) * (1000 + (i * 100)));
                } catch (InterruptedException e) {
                    LOG.error(ERROR + "iniCorte() -> Sleep crear hilos mp4c: ", e);
                }
            }
        }
        if (hilos_mp4d > 0) {
            hilos_mp4d += 1;
            for (int i = 1; i < hilos_mp4d; i++) {
                CorteMp4 corte_mp4 = new CorteMp4("MP4d-" + i + ">", "6");
                corte_mp4.start();
                try {
                    Thread.sleep((hilos_mp4d - i) * (1000 + (i * 100)));
                } catch (InterruptedException e) {
                    LOG.error(ERROR + "iniCorte() -> Sleep crear hilos mp4d: ", e);
                }
            }
        }
    }

}
