package com.intelite.threads;

import static com.intelite.cutter.CutterApp.DS;
import static com.intelite.cutter.CutterApp.DF;
import static com.intelite.cutter.CutterApp.DIR_TMP;
import static com.intelite.cutter.CutterApp.ERROR;
import static com.intelite.cutter.CutterApp.montaje;
import static com.intelite.cutter.CutterApp.carpeta;
import static com.intelite.cutter.CutterApp.origen;
import static com.intelite.cutter.CutterApp.NAME_PU;
import static com.intelite.cutter.CutterApp.PROPS_PU;
import static com.intelite.cutter.CutterApp.DF_MMMM;
import static com.intelite.cutter.CutterApp.DF_YYYY;
import static com.intelite.cutter.CutterApp.DF_MM;
import static com.intelite.cutter.CutterApp.DF_YY;
import static com.intelite.cutter.CutterApp.DF_DD;
import static com.intelite.cutter.CutterApp.ASFBIN_PATH;
import static com.intelite.cutter.CutterApp.FFMPEG_PATH;
import static com.intelite.cutter.CutterApp.FFPROBE_PATH;
import static com.intelite.cutter.CutterApp.brun;
import com.intelite.models.Corte;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.Optional;
import java.util.Random;
import java.util.stream.IntStream;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import net.bramp.ffmpeg.FFmpeg;
import net.bramp.ffmpeg.FFmpegExecutor;
import net.bramp.ffmpeg.FFprobe;
import net.bramp.ffmpeg.builder.FFmpegBuilder;
import net.bramp.ffmpeg.probe.FFmpegFormat;
import net.bramp.ffmpeg.probe.FFmpegProbeResult;
import org.apache.commons.lang3.math.Fraction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CorteMp4 extends Thread {

    private static final Logger LOG = LoggerFactory.getLogger(CorteMp4.class);
    private static final Fraction FPS = Fraction.getFraction(30, 1);
    private String tipo_origen;

    public CorteMp4() {
        super();
    }

    public CorteMp4(String name, String tipo_origen) {
        super(name);
        this.tipo_origen = tipo_origen;
    }

    @Override
    public void run() {
        while (brun) {
            startProcess();
            try {
                Random r = new Random();
                // 1 número aleatorio entre 100 y 200, excluido el 200 (del 100 al 199)
                IntStream intStream = r.ints(1, 100, 200);
                // Iterador para obtener el número
                Iterator iterator = intStream.iterator();
                int delay = (int) iterator.next();
                CorteMp4.sleep(delay * 100);
            } catch (InterruptedException e) {
                LOG.error(this.getName() + ERROR + "InterruptedException - run() -> sleep delay:", e);
            }
        }
    }

    private void startProcess() {
        Optional<Corte> c = getCorte();
        if (c != null) {
            if (c.isPresent()) {
                Corte corte = c.get();
                int capclave = corte.getCapclave();
                updateStatus(capclave, 1); // En proceso
                int status = crearCorte(corte);
                if (status == 2) {
                    copiarTemp(corte);
                } else { // 9 o 0
                    borrarTemp(corte);
                    updateStatus(capclave, status);
                }
            }
        }
    }

    private Optional<Corte> getCorte() {
        System.out.println(DF.format(new Date()) + this.getName() + "Buscando corte...");
        EntityManagerFactory factory = Persistence.createEntityManagerFactory(NAME_PU, PROPS_PU);
        EntityManager em = factory.createEntityManager();
        try {
            return em.createQuery("FROM Corte WHERE status = 0 AND tipo_origen = " + tipo_origen + " AND origen = " + origen + " ORDER BY fecha", Corte.class).getResultList().stream().findFirst();
        } catch (Exception e) {
            LOG.error(this.getName() + ERROR + "Exception -> getCorte():", e);
        } finally {
            em.close();
            factory.close();
        }
        return null;
    }

    private void updateStatus(int capclave, int status) {
        EntityManagerFactory factory = Persistence.createEntityManagerFactory(NAME_PU, PROPS_PU);
        EntityManager em = factory.createEntityManager();
        try {
            em.getTransaction().begin();
            Corte corte = em.find(Corte.class, capclave);
            corte.setStatus(status);
            em.getTransaction().commit();
            LOG.error(this.getName() + "UpdateStatus -> " + capclave + " -> " + status);
        } catch (Exception e) {
            LOG.error(this.getName() + ERROR + "UpdateStatus() -> " + capclave + " -> " + status, e);
        } finally {
            em.close();
            factory.close();
        }
    }

    private int crearCorte(Corte corte) {
        int intentos = corte.getIntentos() + 1;
        if (intentos < 100) {
            updateIntentos(corte.getCapclave(), intentos);
            if (corte.getSrc().toLowerCase().contains(".mp4")) {
                return crearMp4(corte, DIR_TMP + corte.getNombre_archivo());
            } else {
                return crearWmv(corte);
            }
        } else {
            LOG.error(this.getName() + ERROR + "NO SE PUDO GENERAR EL CORTE  -> " + corte.getNombre_archivo());
            return 9; // No se pudo generar el testigo por duración incorrecta
        }
    }

    private void updateIntentos(int capclave, int intentos) {
        EntityManagerFactory factory = Persistence.createEntityManagerFactory(NAME_PU, PROPS_PU);
        EntityManager em = factory.createEntityManager();
        try {
            em.getTransaction().begin();
            Corte corte = em.find(Corte.class, capclave);
            corte.setIntentos(intentos);
            em.getTransaction().commit();
        } catch (Exception e) {
            LOG.error(this.getName() + ERROR + "UpdateIntentos() -> " + capclave + " -> " + intentos, e);
        } finally {
            em.close();
            factory.close();
        }
    }

    private int crearWmv(Corte corte) {
        String filename = corte.getNombre_archivo();
        String dir_tmp_mp4 = DIR_TMP + corte.getNombre_archivo();
        String dir_tmp_wmv = DIR_TMP + corte.getNombre_archivo().replace(".mp4", ".wmv");
        try {
            // Se convierte a segundos el inicio y la duración
            Double start = timeToSeconds(corte.getTime_ini());
            Double dur = timeToSeconds(corte.getDuracion());
            // Se crea el array de comando que ejecutará el asfbin.exe
            String[] cmd = {"wine", ASFBIN_PATH, "-i", corte.getSrc(), "-o", dir_tmp_wmv, "-y", "-start", start.toString(), "-dur", dur.toString()};
//            String[] cmd = {"wine", "/opt/cuttermp4a/asfbin.exe", "-i", corte.getSrc(), "-o", dir_tmp_wmv, "-y", "-start", start.toString(), "-dur", dur.toString(), "-istart"}; // Ajuste exacto de tiempo (si buscar el key frame)
            LOG.error(this.getName() + "INICIA CORTE WMV -> " + filename);
            // Run this on Windows/Linux
            ProcessBuilder processBuilder = new ProcessBuilder();
            processBuilder.command(cmd);
            Process process = processBuilder.start();
            try {
                // Se verifica el código de salida (exit code)
                if (process.waitFor() == 0) {
                    // Se obtiene y valida la duración del corte WMV
                    FFprobe ffprobe = new FFprobe(FFPROBE_PATH);
                    FFmpegProbeResult probeResult = ffprobe.probe(dir_tmp_wmv);
                    FFmpegFormat format = probeResult.getFormat();

                    Double dur_bottom = (dur >= 1) ? (dur - 1) : 0;
                    Double dur_top = dur + 10;

                    if (format.duration >= dur_bottom && format.duration <= dur_top) {
                        LOG.error(this.getName() + "TERMINA CORTE WMV -> " + filename);
                        return crearMp4(corte, dir_tmp_wmv, dir_tmp_mp4);
                    } else {
                        LOG.error(this.getName() + ERROR + "DURACION INCORRECTA WMV -> " + filename + " -> Dur=" + dur + " | " + "DurFinal=" + format.duration);
                        try {
                            CorteMp4.sleep(1000);
                        } catch (InterruptedException e) {
                            LOG.error(this.getName() + ERROR + "InterruptedException - Duracion incorrecta -> " + filename, e);
                        }
                    }

                } else {
                    LOG.error(this.getName() + ERROR + "EN CORTE WMV (ASFBIN) -> " + filename);
                }
            } catch (InterruptedException e) {
                LOG.error(this.getName() + ERROR + "InterruptedException en crearWmv() -> " + filename, e);
            }
        } catch (IOException e) {
            LOG.error(this.getName() + ERROR + "IOException en crearWmv() -> " + filename, e);
        }
        return 0;
    }

    private Double timeToSeconds(String time) {
        String[] time_parts = time.trim().split(":");
        Double hh = Double.parseDouble(time_parts[0]) * 3600;
        Double mm = Double.parseDouble(time_parts[1]) * 60;
        Double ss = Double.parseDouble(time_parts[2]);
        return hh + mm + ss;
    }

    private int crearMp4(Corte corte, String dir_tmp_wmv, String dir_tmp_mp4) {
        String filename = corte.getNombre_archivo();
        try {
            FFmpeg ffmpeg = new FFmpeg(FFMPEG_PATH);
            FFmpegBuilder builder = new FFmpegBuilder()
                    .setInput(dir_tmp_wmv) // Filename, or a FFmpegProbeResult
                    .overrideOutputFiles(true) // Override the output if it exists
                    .addOutput(dir_tmp_mp4) // Filename for the destination
                    // AUDIO 
                    .setAudioCodec("aac")
                    .setAudioBitRate(48000) // audio bitrate to be exact -> 48, 64, 96, 128, 160, 192, 256 kbit
                    .setAudioSampleRate(32000) // audio sampling frequenc (Hz) -> 32000, 44100, 48000
                    .setAudioChannels(FFmpeg.AUDIO_STEREO) // Stereo audio
                    // VIDEO
                    .setVideoCodec("libx264")
                    .setVideoResolution(640, 480)
                    .setVideoBitRate(500000)
                    .setVideoFrameRate(FPS)
                    .addExtraArgs("-crf", "23") // Set the quality/size
                    .addExtraArgs("-profile:v", "baseline") //main or high
                    .done();

            FFmpegExecutor executor = new FFmpegExecutor(ffmpeg);

            LOG.error(this.getName() + "INICIA CONVERSIÓN MP4 -> " + filename);

            // Run a one-pass encode
            executor.createJob(builder).run();
            // Or run a two-pass encode (which is better quality at the cost of being slower)
//            executor.createTwoPassJob(builder).run();

            LOG.error(this.getName() + "TERMINA CONVERSIÓN MP4 -> " + filename);
            // Se obtiene y valida el tamaño y duración del archivo resultante
            try {
                FFprobe ffprobe = new FFprobe(FFPROBE_PATH);
                FFmpegProbeResult probeResult = ffprobe.probe(dir_tmp_mp4);
                FFmpegFormat format = probeResult.getFormat();
                if (format.size > 0 && format.duration > 0) {
                    return 2; // MP4 CREADO
                }
            } catch (IOException e) {
                LOG.error(this.getName() + ERROR + "ARCHIVO NO VÁLIDO (0 bytes) -> " + filename, e);
            }
        } catch (IOException e) {
            LOG.error(this.getName() + ERROR + "AL EJECUTAR FFMPEG -> " + filename, e);
        }
        return 0;
    }

    private int crearMp4(Corte corte, String dir_tmp_mp4) {
        String filename = corte.getNombre_archivo();
        try {
            FFmpeg ffmpeg = new FFmpeg(FFMPEG_PATH);
            FFmpegBuilder builder = new FFmpegBuilder()
                    .setInput(corte.getSrc()) // Filename, or a FFmpegProbeResult
                    .overrideOutputFiles(true) // Override the output if it exists
                    .addOutput(dir_tmp_mp4) // Filename for the destination
                    // Se copian los codecs de audio y video (para no recodificar)
                    .addExtraArgs("-ss", corte.getTime_ini())
                    .addExtraArgs("-t", corte.getDuracion()) // Establece duración
                    .addExtraArgs("-c:v", "copy")
                    .addExtraArgs("-c:a", "aac")
                    .addExtraArgs("-b:a", "128k")
                    .done();

            FFmpegExecutor executor = new FFmpegExecutor(ffmpeg);

            LOG.error(this.getName() + "INICIA CORTE MP4 -> " + filename);

            // Run a one-pass encode
            executor.createJob(builder).run();
            // Or run a two-pass encode (which is better quality at the cost of being slower)
//            executor.createTwoPassJob(builder).run();

            LOG.error(this.getName() + "TERMINA CORTE MP4 -> " + filename);
            // Se obtiene y valida el tamaño y duración del archivo resultante
            try {
                FFprobe ffprobe = new FFprobe(FFPROBE_PATH);
                FFmpegProbeResult probeResult = ffprobe.probe(dir_tmp_mp4);
                FFmpegFormat format = probeResult.getFormat();
                if (format.size > 0 && format.duration > 0) {
                    return 2; // MP4 CREADO
                }
            } catch (IOException e) {
                LOG.error(this.getName() + ERROR + "ARCHIVO NO VÁLIDO (0 bytes) -> " + filename, e);
            }
        } catch (IOException e) {
            LOG.error(this.getName() + ERROR + "AL EJECUTAR FFMPEG -> " + filename, e);
        }
        return 0;
    }

    private void copiarTemp(Corte corte) {
        Integer capclave = corte.getCapclave();
        String filename = corte.getNombre_archivo();
        String dir_tmp = DIR_TMP + filename;
        try {
            // Se crea el directorio de destino ("/documentos16/yyyy/%MM%MMMMyy/Imagenes_ddMMyyyy/")
            Date date = new SimpleDateFormat("ddMMyyyy").parse(filename.substring(0, 8));
            String mes = DF_MMMM.format(date);
            mes = Character.toUpperCase(mes.charAt(0)) + mes.substring(1); // Se convierte en mayúscula la primera letra del mes
            String dir_des = DS + montaje + DF_YYYY.format(date) + DS + DF_MM.format(date) + mes + DF_YY.format(date)
                    + DS + carpeta + DF_DD.format(date) + DF_MM.format(date) + DF_YYYY.format(date) + DS + filename;

            File file_tmp = new File(dir_tmp);
            if (file_tmp.exists() && file_tmp.length() > 0) {
                System.out.println(DF.format(new Date()) + this.getName() + "Copiando " + filename + " en Testigos...");
                File fsrc = new File(dir_tmp);
                File fdes = new File(dir_des);
                // Copy file conventional way using Stream
                InputStream is = null;
                OutputStream os = null;
                try {
                    is = new FileInputStream(fsrc);
                    os = new FileOutputStream(fdes);
                    byte[] buffer = new byte[1024];
                    int length;
                    while ((length = is.read(buffer)) > 0) {
                        os.write(buffer, 0, length);
                    }
                    os.close(); // Cierre OutputStream
                    is.close(); // Cierre InputStream
                    LOG.error(this.getName() + "ARCHIVO " + filename + " COPIADO EN TESTIGOS!");
                    updateStatus(capclave, 2); // Temporal copiado con éxito
                    borrarTemp(corte);

                } catch (IOException e) {
                    LOG.error(this.getName() + ERROR + "NO SE PUDO COPIAR " + filename + " EN TESTIGOS", e);
                    // Se verifica si ya está en la carpeta de testigos
                    File file_des = new File(dir_des);
                    if (file_des.exists() && file_des.length() > 0) {
                        updateStatus(capclave, 2); // Temporal copiado con éxito
                        borrarTemp(corte);
                    } else {
                        updateStatus(capclave, 0); // Volver a generar testigo
                    }
                } finally {
                    try {
                        if (is != null) {
                            is.close();
                        }
                        if (os != null) {
                            os.close();
                        }
                    } catch (IOException e) {
                        LOG.error(this.getName() + ERROR + "Al cerrar stream en copiarTemp() -> " + filename, e);
                    }
                }
            } else {
                // Se verifica si ya está en la carpeta de testigos
                File file_des = new File(dir_des);
                if (file_des.exists() && file_des.length() > 0) {
                    updateStatus(capclave, 2); // Temporal copiado con éxito
                } else {
                    updateStatus(capclave, 0); // Volver a generar testigo
                }
            }
        } catch (ParseException e) {
            LOG.error(this.getName() + ERROR + "EN NOMBRE DE TESTIGO -> " + filename, e);
            updateStatus(capclave, 9); // Error en nombre de testigo
        }
    }

    private void borrarTemp(Corte corte) {
        try {
            System.out.println(DF.format(new Date()) + this.getName() + "Borrando temporal -> " + corte.getNombre_archivo());
            Files.deleteIfExists(Paths.get(DIR_TMP + corte.getNombre_archivo()));

            if (corte.getSrc().toLowerCase().contains(".wmv")) {
                String filewmv = corte.getNombre_archivo().replace(".mp4", ".wmv");
                System.out.println(DF.format(new Date()) + this.getName() + "Borrando temporal -> " + filewmv);
                Files.deleteIfExists(Paths.get(DIR_TMP + filewmv));
            }

        } catch (IOException e) {
            LOG.error(this.getName() + ERROR + "No se pudo eliminar temporal -> ", e);
        }
    }
}
