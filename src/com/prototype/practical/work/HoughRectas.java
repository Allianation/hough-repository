package com.prototype.practical.work;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class HoughRectas {

    // ------------------------------------------------------------------
    // Estructuras de datos
    // ------------------------------------------------------------------

    /** Espacio de parametros discretizado (rho, theta). */
    static class Acumulador {
        final int[][] celdas;   // celdas[rho + diag][indiceTheta]
        final int diag;         // desplazamiento (offset) para evitar indices negativos
        final int nTheta;       // cantidad de angulos
        final double resTheta;  // resolucion angular en grados

        Acumulador(int diag, int nTheta, double resTheta) {
            this.diag = diag;
            this.nTheta = nTheta;
            this.resTheta = resTheta;
            this.celdas = new int[2 * diag + 1][nTheta];
        }

        int nRho() {
            return celdas.length;
        }
    }

    /** Recta detectada: un pico del acumulador. */
    static class Recta {
        final double rho;
        final double theta;   // en grados
        final int votos;

        Recta(double rho, double theta, int votos) {
            this.rho = rho;
            this.theta = theta;
            this.votos = votos;
        }

        @Override
        public String toString() {
            return String.format("rho = %6.1f px | theta = %5.1f grados | votos = %d", rho, theta, votos);
        }
    }

    // ------------------------------------------------------------------
    // Generacion de imagenes de prueba (matriz binaria: 1 = borde, 0 = fondo)
    // ------------------------------------------------------------------

    static int[][] imagenVacia(int ancho, int alto) {
        return new int[alto][ancho];
    }

    /** Dibuja un segmento entre dos puntos (algoritmo simple por interpolacion). */
    static void dibujarSegmento(int[][] img, int x1, int y1, int x2, int y2) {
        int pasos = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1));
        for (int i = 0; i <= pasos; i++) {
            double t = (pasos == 0) ? 0 : (double) i / pasos;
            int x = (int) Math.round(x1 + t * (x2 - x1));
            int y = (int) Math.round(y1 + t * (y2 - y1));
            if (y >= 0 && y < img.length && x >= 0 && x < img[0].length) {
                img[y][x] = 1;
            }
        }
    }

    /** Agrega pixeles de ruido ("sal") en posiciones aleatorias. */
    static void agregarRuido(int[][] img, int cantidad, Random rnd) {
        for (int i = 0; i < cantidad; i++) {
            img[rnd.nextInt(img.length)][rnd.nextInt(img[0].length)] = 1;
        }
    }

    static int contarBordes(int[][] img) {
        int n = 0;
        for (int[] fila : img) {
            for (int v : fila) {
                if (v != 0) n++;
            }
        }
        return n;
    }

    // ------------------------------------------------------------------
    // Transformada de Hough (votacion)
    // ------------------------------------------------------------------

    static Acumulador transformada(int[][] img, double resTheta) {
        int alto = img.length;
        int ancho = img[0].length;

        // rho puede valer entre -diagonal y +diagonal de la imagen
        int diag = (int) Math.ceil(Math.hypot(ancho, alto));
        int nTheta = (int) Math.round(180.0 / resTheta);
        Acumulador acc = new Acumulador(diag, nTheta, resTheta);

        // Se precalculan seno y coseno una sola vez (evita recalcularlos por cada pixel)
        double[] cos = new double[nTheta];
        double[] sen = new double[nTheta];
        for (int t = 0; t < nTheta; t++) {
            double rad = Math.toRadians(t * resTheta);
            cos[t] = Math.cos(rad);
            sen[t] = Math.sin(rad);
        }

        // Cada pixel de borde vota por todas las rectas (rho, theta) que pasan por el
        for (int y = 0; y < alto; y++) {
            for (int x = 0; x < ancho; x++) {
                if (img[y][x] == 0) continue;
                for (int t = 0; t < nTheta; t++) {
                    int rho = (int) Math.round(x * cos[t] + y * sen[t]);
                    acc.celdas[rho + diag][t]++;   // + diag: desplazamiento para indices >= 0
                }
            }
        }
        return acc;
    }

    // ------------------------------------------------------------------
    // Busqueda de picos con supresion de no-maximos
    // ------------------------------------------------------------------

    /**
     * Busca las celdas con mas votos que superen el umbral. Tras elegir un pico se anula su
     * vecindario (+-distMin celdas) para no reportar la misma recta varias veces.
     * Como (rho, theta) y (-rho, theta + 180) son la misma recta, el vecindario tambien se
     * "envuelve" en los extremos de theta (rectas casi verticales).
     * Con distMin = 0 no hay supresion (sirve para mostrar el problema de los picos multiples).
     */
    static List<Recta> buscarPicos(Acumulador acc, int umbral, int distMin) {
        int nRho = acc.nRho();
        int[][] copia = new int[nRho][];
        for (int r = 0; r < nRho; r++) copia[r] = acc.celdas[r].clone();

        List<Recta> rectas = new ArrayList<>();
        while (true) {
            int max = 0, rMax = 0, tMax = 0;
            // Se recorre rho de mayor a menor: ante un empate entre (rho, theta) y su recta
            // equivalente (-rho, theta + 180) se informa la de rho positivo (p. ej. vertical: theta = 0).
            for (int r = nRho - 1; r >= 0; r--) {
                for (int t = 0; t < acc.nTheta; t++) {
                    if (copia[r][t] > max) {
                        max = copia[r][t];
                        rMax = r;
                        tMax = t;
                    }
                }
            }
            if (max < umbral || max == 0) break;

            // Si varios angulos contiguos empatan (segmentos cortos: la resolucion angular no
            // alcanza para distinguirlos), se informa el centro de esa "meseta".
            int tFin = tMax;
            while (tFin + 1 < acc.nTheta && copia[rMax][tFin + 1] == max) tFin++;
            int tCentro = (tMax + tFin) / 2;

            rectas.add(new Recta(rMax - acc.diag, (tMax + tFin) / 2.0 * acc.resTheta, max));

            for (int dt = -distMin; dt <= distMin; dt++) {
                int t = tCentro + dt;
                boolean espejo = false;
                if (t < 0) {
                    t += acc.nTheta;
                    espejo = true;
                } else if (t >= acc.nTheta) {
                    t -= acc.nTheta;
                    espejo = true;
                }
                int rCentro = espejo ? 2 * acc.diag - rMax : rMax;
                for (int dr = -distMin; dr <= distMin; dr++) {
                    int r = rCentro + dr;
                    if (r >= 0 && r < nRho) copia[r][t] = 0;
                }
            }
        }
        return rectas;
    }

    // ------------------------------------------------------------------
    // Visualizacion en consola
    // ------------------------------------------------------------------

    /** '#' borde de la imagen, '+' recta detectada, '@' borde que coincide con una recta. */
    static void imprimirASCII(int[][] img, List<Recta> rectas) {
        for (int y = 0; y < img.length; y++) {
            StringBuilder sb = new StringBuilder();
            for (int x = 0; x < img[0].length; x++) {
                boolean borde = img[y][x] != 0;
                boolean enRecta = false;
                for (Recta r : rectas) {
                    double th = Math.toRadians(r.theta);
                    if (Math.abs(x * Math.cos(th) + y * Math.sin(th) - r.rho) < 0.5) {
                        enRecta = true;
                        break;
                    }
                }
                sb.append(borde && enRecta ? '@' : borde ? '#' : enRecta ? '+' : '.');
            }
            System.out.println(sb);
        }
    }

    static void ejecutar(String titulo, int[][] img, double resTheta, int umbral, int distMin, boolean ascii) {
        System.out.println("============================================================");
        System.out.println(titulo);
        System.out.println("============================================================");
        int bordes = contarBordes(img);
        Acumulador acc = transformada(img, resTheta);
        List<Recta> rectas = buscarPicos(acc, umbral, distMin);

        System.out.printf("Imagen: %d x %d px | pixeles de borde: %d%n", img[0].length, img.length, bordes);
        System.out.printf("Acumulador: %d (rho) x %d (theta) | votos emitidos: %d%n",
                acc.nRho(), acc.nTheta, (long) bordes * acc.nTheta);
        System.out.printf("Umbral = %d votos | distancia minima entre picos = %d celdas%n", umbral, distMin);
        System.out.println("Rectas detectadas: " + rectas.size());
        int limite = Math.min(rectas.size(), 8);
        for (int i = 0; i < limite; i++) {
            System.out.println("  Recta " + (i + 1) + ": " + rectas.get(i));
        }
        if (rectas.size() > limite) {
            System.out.println("  ... (" + (rectas.size() - limite) + " rectas mas)");
        }
        if (ascii) {
            System.out.println();
            imprimirASCII(img, rectas);
        }
        System.out.println();
    }

    // ------------------------------------------------------------------
    // Casos de prueba
    // ------------------------------------------------------------------

    public static void main(String[] args) {

        // PRUEBA 1: caso sencillo y visible. Una horizontal, una vertical y una diagonal.
        int[][] img1 = imagenVacia(60, 30);
        dibujarSegmento(img1, 5, 10, 54, 10);   // horizontal  -> theta = 90, rho = 10
        dibujarSegmento(img1, 30, 2, 30, 27);   // vertical    -> theta = 0,  rho = 30
        dibujarSegmento(img1, 18, 27, 45, 0);   // diagonal    -> theta = 45, rho = 32
        ejecutar("PRUEBA 1 - Tres rectas limpias (horizontal, vertical, diagonal)", img1, 1.0, 20, 5, true);

        // PRUEBA 2: bordes incompletos (huecos) + ruido, como en la planta (sombras, reflejos, etc.)
        int[][] img2 = imagenVacia(200, 200);
        for (int x = 20; x < 180; x += 30) {
            dibujarSegmento(img2, x, 100, Math.min(x + 19, 179), 100);   // tramos de 20 px, huecos de 10 px
        }
        for (int i = 40; i < 160; i += 30) {
            dibujarSegmento(img2, i, 199 - i, Math.min(i + 19, 159), 199 - Math.min(i + 19, 159));
        }
        agregarRuido(img2, 300, new Random(42));
        ejecutar("PRUEBA 2 - Rectas con huecos + 300 pixeles de ruido (umbral adecuado = 50)", img2, 1.0, 50, 10, false);

        // PRUEBA 3: la misma imagen con un umbral demasiado bajo -> falsas rectas
        ejecutar("PRUEBA 3 - Mismo caso con umbral demasiado bajo (= 12)", img2, 1.0, 12, 10, false);

        // PRUEBA 4: recta gruesa (3 px). Sin supresion de no-maximos aparecen picos duplicados.
        int[][] img4 = imagenVacia(200, 200);
        for (int y = 99; y <= 101; y++) {
            dibujarSegmento(img4, 20, y, 179, y);
        }
        ejecutar("PRUEBA 4a - Recta gruesa SIN supresion de no-maximos (distMin = 0)", img4, 1.0, 120, 0, false);
        ejecutar("PRUEBA 4b - Recta gruesa CON supresion de no-maximos (distMin = 10)", img4, 1.0, 120, 10, false);
    }
}
