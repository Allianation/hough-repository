package com.prototype.practical.work;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class HoughCircunferencias {

    // ------------------------------------------------------------------
    // Estructuras de datos
    // ------------------------------------------------------------------

    /** Centro candidato: un pico del acumulador. */
    static class Centro {
        final int x, y, votos;

        Centro(int x, int y, int votos) {
            this.x = x;
            this.y = y;
            this.votos = votos;
        }

        @Override
        public String toString() {
            return String.format("centro = (x0 = %3d, y0 = %3d) | votos = %d", x, y, votos);
        }
    }

    // ------------------------------------------------------------------
    // Generacion de imagenes de prueba (matriz binaria: 1 = borde, 0 = fondo)
    // ------------------------------------------------------------------

    static int[][] imagenVacia(int ancho, int alto) {
        return new int[alto][ancho];
    }

    /**
     * Dibuja una circunferencia de centro (cx, cy) y radio r.
     * Si ocIni >= 0, no se dibujan los angulos entre ocIni y ocFin (simula oclusion: herramienta, sombra).
     */
    static void dibujarCircunferencia(int[][] img, int cx, int cy, int r, double ocIni, double ocFin) {
        for (double ang = 0; ang < 360; ang += 0.5) {
            if (ocIni >= 0 && ang >= ocIni && ang <= ocFin) continue;
            int x = (int) Math.round(cx + r * Math.cos(Math.toRadians(ang)));
            int y = (int) Math.round(cy + r * Math.sin(Math.toRadians(ang)));
            if (y >= 0 && y < img.length && x >= 0 && x < img[0].length) {
                img[y][x] = 1;
            }
        }
    }

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
    // Transformada de Hough para circunferencias de radio fijo
    // ------------------------------------------------------------------

    /**
     * Devuelve el acumulador de centros acc[y0][x0], del mismo tamano que la imagen.
     * Un mismo pixel de borde vota como maximo UNA vez por celda (aunque dos angulos
     * cercanos caigan en la misma celda), asi el valor de una celda es "cuantos pixeles
     * de borde distintos apuntan a ese centro".
     */
    static int[][] transformada(int[][] img, int radio, double resAngulo) {
        int alto = img.length;
        int ancho = img[0].length;
        int[][] acc = new int[alto][ancho];
        int[][] ultimoVoto = new int[alto][ancho];   // marca: id del ultimo pixel que voto la celda

        int nAng = (int) Math.round(360.0 / resAngulo);
        double[] cos = new double[nAng];
        double[] sen = new double[nAng];
        for (int a = 0; a < nAng; a++) {
            cos[a] = Math.cos(Math.toRadians(a * resAngulo));
            sen[a] = Math.sin(Math.toRadians(a * resAngulo));
        }

        int id = 0;
        for (int y = 0; y < alto; y++) {
            for (int x = 0; x < ancho; x++) {
                if (img[y][x] == 0) continue;
                id++;
                for (int a = 0; a < nAng; a++) {
                    int x0 = (int) Math.round(x - radio * cos[a]);
                    int y0 = (int) Math.round(y - radio * sen[a]);
                    // validacion de frontera: el centro candidato debe caer dentro del acumulador
                    if (x0 >= 0 && x0 < ancho && y0 >= 0 && y0 < alto && ultimoVoto[y0][x0] != id) {
                        ultimoVoto[y0][x0] = id;
                        acc[y0][x0]++;
                    }
                }
            }
        }
        return acc;
    }

    /** Busca picos por encima del umbral, con supresion de no-maximos (vecindario +-distMin). */
    static List<Centro> buscarCentros(int[][] acc, int umbral, int distMin, int maxCentros) {
        int alto = acc.length, ancho = acc[0].length;
        int[][] copia = new int[alto][];
        for (int y = 0; y < alto; y++) copia[y] = acc[y].clone();

        List<Centro> centros = new ArrayList<>();
        while (centros.size() < maxCentros) {
            int max = 0, yMax = 0, xMax = 0;
            for (int y = 0; y < alto; y++) {
                for (int x = 0; x < ancho; x++) {
                    if (copia[y][x] > max) {
                        max = copia[y][x];
                        yMax = y;
                        xMax = x;
                    }
                }
            }
            if (max < umbral || max == 0) break;
            centros.add(new Centro(xMax, yMax, max));
            for (int y = Math.max(0, yMax - distMin); y <= Math.min(alto - 1, yMax + distMin); y++) {
                for (int x = Math.max(0, xMax - distMin); x <= Math.min(ancho - 1, xMax + distMin); x++) {
                    copia[y][x] = 0;
                }
            }
        }
        return centros;
    }

    // ------------------------------------------------------------------
    // Visualizacion en consola
    // ------------------------------------------------------------------

    static void imprimirImagen(int[][] img) {
        for (int[] fila : img) {
            StringBuilder sb = new StringBuilder();
            for (int v : fila) sb.append(v != 0 ? '#' : '.').append(' ');
            System.out.println(sb);
        }
    }

    static void imprimirAcumulador(int[][] acc) {
        for (int[] fila : acc) {
            StringBuilder sb = new StringBuilder();
            for (int v : fila) sb.append(String.format("%2d ", v));
            System.out.println(sb);
        }
    }

    static void encabezado(String titulo) {
        System.out.println("============================================================");
        System.out.println(titulo);
        System.out.println("============================================================");
    }

    /** Ejecuta una deteccion con radio fijo e informa el mejor centro, el segundo pico y la correccion. */
    static Centro ejecutar(int[][] img, int radio, double resAngulo, int umbral, int refX, int refY) {
        int bordes = contarBordes(img);
        int[][] acc = transformada(img, radio, resAngulo);
        List<Centro> centros = buscarCentros(acc, umbral, Math.max(2, radio / 4), 3);

        System.out.printf("Imagen: %d x %d px | pixeles de borde: %d | radio buscado R = %d px%n",
                img[0].length, img.length, bordes, radio);
        System.out.printf("Resolucion angular: %.1f grados (%d angulos) | votos emitidos: %d%n",
                resAngulo, (int) Math.round(360 / resAngulo), (long) bordes * Math.round(360 / resAngulo));
        if (centros.isEmpty()) {
            System.out.println("No se encontro ningun centro por encima del umbral (" + umbral + " votos).");
            System.out.println();
            return null;
        }
        Centro mejor = centros.get(0);
        System.out.println("Mejor candidato : " + mejor);
        if (centros.size() > 1) {
            System.out.println("Segundo pico    : " + centros.get(1)
                    + String.format("  (relacion = %.1f)",
                    (double) mejor.votos / centros.get(1).votos));
        }
        if (refX >= 0) {
            System.out.printf("Referencia A_ref = (%d, %d) -> correccion: dx = %+d px, dy = %+d px%n",
                    refX, refY, mejor.x - refX, mejor.y - refY);
        }
        System.out.println();
        return mejor;
    }

    // ------------------------------------------------------------------
    // Extension: barrido de radios (cuando R NO es conocido) -> espacio 3D (x0, y0, R)
    // ------------------------------------------------------------------

    static void barridoDeRadios(int[][] img, int rMin, int rMax) {
        int mejorR = -1;
        Centro mejor = null;
        List<String> tabla = new ArrayList<>();
        for (int r = rMin; r <= rMax; r++) {
            int[][] acc = transformada(img, r, 1.0);
            List<Centro> c = buscarCentros(acc, 1, Math.max(2, r / 4), 1);
            if (!c.isEmpty()) {
                tabla.add(String.format("  R = %2d -> mejor pico (%3d, %3d) con %3d votos", r, c.get(0).x, c.get(0).y, c.get(0).votos));
                if (mejor == null || c.get(0).votos > mejor.votos) {
                    mejor = c.get(0);
                    mejorR = r;
                }
            }
        }
        for (String f : tabla) System.out.println(f);
        long emitidos = (long) contarBordes(img) * 360 * (rMax - rMin + 1);
        System.out.printf("Mejor (x0, y0, R) = (%d, %d, %d) con %d votos | %d radios | votos emitidos: %d%n",
                mejor.x, mejor.y, mejorR, mejor.votos, rMax - rMin + 1, emitidos);
        System.out.println();
    }

    // ------------------------------------------------------------------
    // Casos de prueba
    // ------------------------------------------------------------------

    public static void main(String[] args) {

        // PRUEBA 1: caso minimo: 4 puntos A, B, C, D
        // (norte, oeste, este, sur) de una circunferencia de radio 2 con centro en (3, 3).
        encabezado("PRUEBA 1 - Caso minimo: 4 pixeles de una circunferencia de R = 2 (imagen 7x7)");
        int[][] img1 = imagenVacia(7, 7);
        img1[1][3] = 1;   // norte
        img1[3][1] = 1;   // oeste
        img1[3][5] = 1;   // este
        img1[5][3] = 1;   // sur
        System.out.println("Imagen de bordes:");
        imprimirImagen(img1);
        int[][] acc1 = transformada(img1, 2, 1.0);
        System.out.println("\nAcumulador de centros (x0, y0):");
        imprimirAcumulador(acc1);
        List<Centro> c1 = buscarCentros(acc1, 1, 1, 2);
        System.out.println("\nMejor candidato : " + c1.get(0));
        System.out.println("Segundo pico    : " + c1.get(1));
        System.out.println();

        // PRUEBA 2: el block se desplazo sobre la cinta (error de posicionamiento en X e Y)
        encabezado("PRUEBA 2 - Block desplazado: el aro R = 30 aparece centrado en (65, 40)");
        int[][] img2 = imagenVacia(100, 100);
        dibujarCircunferencia(img2, 65, 40, 30, -1, -1);
        ejecutar(img2, 30, 1.0, 20, 50, 50);

        // PRUEBA 3: aro parcialmente tapado (herramienta de montaje): se oculta el 40% del aro
        encabezado("PRUEBA 3 - Aro parcialmente oculto (se omiten 144 grados de los 360)");
        int[][] img3 = imagenVacia(100, 100);
        dibujarCircunferencia(img3, 50, 50, 30, 20, 164);
        ejecutar(img3, 30, 1.0, 20, 50, 50);

        // PRUEBA 4: escena realista: aro exterior + aro interior (los dos bordes del anillo "C"),
        // otra circunferencia pequena, una recta y 400 pixeles de ruido
        encabezado("PRUEBA 4 - Escena con ruido: aro doble, circunferencia menor, recta y 400 pixeles de ruido");
        int[][] img4 = imagenVacia(100, 100);
        dibujarCircunferencia(img4, 50, 50, 30, -1, -1);   // borde exterior del anillo
        dibujarCircunferencia(img4, 50, 50, 22, -1, -1);   // borde interior del anillo
        dibujarCircunferencia(img4, 18, 82, 8, -1, -1);    // otra perforacion
        dibujarSegmento(img4, 5, 5, 95, 12);               // borde de otra pieza
        agregarRuido(img4, 400, new Random(7));
        ejecutar(img4, 30, 1.0, 20, 50, 50);

        // Aro completo de referencia (R = 30, centro (50, 50)) para las pruebas 5 a 7
        int[][] ref = imagenVacia(100, 100);
        dibujarCircunferencia(ref, 50, 50, 30, -1, -1);

        // PRUEBA 5: radio mal calibrado (la camara se acerco o se alejo): el radio esta "quemado" en el codigo
        encabezado("PRUEBA 5 - Radio mal calibrado: aro real R = 30 pero se busca R = 25 y R = 36");
        System.out.println("Busqueda con R = 25:");
        ejecutar(ref, 25, 1.0, 5, 50, 50);
        System.out.println("Busqueda con R = 36:");
        ejecutar(ref, 36, 1.0, 5, 50, 50);

        // PRUEBA 6 (adicional): si el radio no se conoce hay que barrer varios radios (espacio 3D)
        encabezado("PRUEBA 6 - Radio desconocido: barrido de R = 26..34 sobre el aro de referencia");
        barridoDeRadios(ref, 26, 34);
        System.out.println("Comparacion con radio fijo (R = 30), misma imagen:");
        ejecutar(ref, 30, 1.0, 20, -1, -1);

        // PRUEBA 7 (adicional): influencia de la resolucion angular
        encabezado("PRUEBA 7 - Resolucion angular: 1 grado vs 10 grados vs 30 grados (aro R = 30 completo)");
        for (double res : new double[]{1.0, 10.0, 30.0}) {
            ejecutar(ref, 30, res, 5, 50, 50);
        }
    }
}
