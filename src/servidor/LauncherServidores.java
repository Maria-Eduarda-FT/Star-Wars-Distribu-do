package servidor;

import bases.ServidorBase;
import comum.TipoEntidade;

public class LauncherServidores {

    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════╗");
        System.out.println("║   INICIANDO SISTEMA DISTRIBUÍDO STAR WARS       ║");
        System.out.println("╚══════════════════════════════════════════════════╝");
        System.out.println();

        Thread threadGalaxia = new Thread(() -> {
            Galaxia galaxia = new Galaxia();
            galaxia.iniciar();
        }, "Thread-Galaxia");

        Thread threadBaseRebelde = new Thread(() -> {
            ServidorBase base = new ServidorBase(
                    "base_rebelde_1",
                    TipoEntidade.BASE_REBELDE,
                    5556,
                    2,    // 2 naves simultâneas
                    1000  // 1000 recursos
            );
            base.iniciar();
        }, "Thread-BaseRebelde");

        Thread threadBaseImperial = new Thread(() -> {
            ServidorBase base = new ServidorBase(
                    "star_destroyer_1",
                    TipoEntidade.STAR_DESTROYER,
                    5557,
                    3,    // 3 naves simultâneas
                    2000  // 2000 recursos
            );
            base.iniciar();
        }, "Thread-BaseImperial");

        Thread threadPlaneta = new Thread(() -> {
            ServidorBase base = new ServidorBase(
                    "planeta_neutro",
                    TipoEntidade.PLANETA,
                    5558,
                    1,    // 1 nave por vez
                    500   // 500 recursos
            );
            base.iniciar();
        }, "Thread-Planeta");

        try {
            System.out.println("[LAUNCHER] Iniciando Servidor Galáxia...");
            threadGalaxia.start();
            Thread.sleep(2000);

            System.out.println("[LAUNCHER] Iniciando Base Rebelde...");
            threadBaseRebelde.start();
            Thread.sleep(1000);

            System.out.println("[LAUNCHER] Iniciando Base Imperial...");
            threadBaseImperial.start();
            Thread.sleep(1000);

            System.out.println("[LAUNCHER] Iniciando Planeta Neutro...");
            threadPlaneta.start();

            System.out.println();
            System.out.println("╔══════════════════════════════════════════════════╗");
            System.out.println("║   TODOS OS SERVIDORES INICIADOS!                ║");
            System.out.println("║   Pressione Ctrl+C para encerrar                ║");
            System.out.println("╚══════════════════════════════════════════════════╝");

        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        try {
            threadGalaxia.join();
            threadBaseRebelde.join();
            threadBaseImperial.join();
            threadPlaneta.join();
        } catch (InterruptedException e) {
            System.err.println("[LAUNCHER] Encerramento interrompido");
        }
    }
}