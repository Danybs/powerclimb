# PowerClimb

Campo de datos gráfico para la Hammerhead Karoo 3 que muestra la **potencia media de la subida activa del Climber nativo**.

La pantalla es deliberadamente minimalista y de alto contraste: negro, blanco y amarillo, pensada para leerse con guantes y con el brillo bajo de la Karoo.

## Qué hace

- Detecta el inicio y el fin de una subida usando los streams nativos del Climber:
  - `ELEVATION_TO_TOP`
  - `DISTANCE_TO_TOP`
  - `CLIMB_NUMBER`
- Reinicia el acumulador al empezar una subida nueva, al terminar la subida o al terminar la ruta.
- Mantiene la media acumulada aunque cambies de página mientras la subida sigue activa. El tracker vive en el servicio de la extensión, no en la vista.
- Muestra:
  - `POT. SUBIDA` como etiqueta.
  - La media en vatios, en grande.
  - La potencia actual y el tiempo acumulado como información secundaria.
  - `FIN · mm:ss` al terminar la subida.
  - `SIN SUBIDA` cuando no hay una subida activa.
  - `PAUSA · mm:ss` si la ruta está pausada.

## Estado del proyecto

Versión `0.1.0` (MVP).

- Media **ponderada por tiempo**.
- Los ceros de potencia cuentan mientras haya señal y la ruta no esté pausada.
- Los huecos de potencia de más de 3 segundos se excluyen de la media.
- Al detectar el coronamiento se congela la media inmediatamente. El final se confirma tras 2 segundos de señal explícita a cero, o tras 5 segundos si el stream deja de emitir, para no cortar por un fallo puntual del GPS o del stream.
- La normalizada (NP) queda para una versión posterior. El diseño ya guarda la información necesaria para añadirla sin rehacer el tracker.

## Requisitos

- Hammerhead Karoo 3.
- Climber nativo disponible y activado.
- Medidor de potencia emparejado con la Karoo.
- Para compilar:
  - JDK 17.
  - Android SDK con `platforms;android-34`, `build-tools;34.0.0` y `platform-tools`.
  - Las dependencias se descargan de Maven Central, Google y JitPack.

## Compilar

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"

./gradlew test
./gradlew :app:assembleDebug
```

El APK queda en:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Si tu SDK está en otra ruta, cambia `sdk.dir` en `local.properties` o exporta `ANDROID_HOME` antes de compilar.

## Instalar en la Karoo

Con la Karoo conectada por USB y la depuración ADB activada:

```bash
adb devices
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Después, en la Karoo:

1. Abre los ajustes de perfiles/páginas.
2. Edita una página y añade un campo de datos.
3. Busca `PowerClimb` y elige `Pot. Subida`.
4. Ajusta el tamaño del campo. El diseño tiene variante compacta y variante grande.

El APK de debug está firmado con la clave de debug. Para distribuir una versión release hace falta configurar un keystore propio.

## Validación en dispositivo

La lógica de detección está probada con tests unitarios, pero el comportamiento real depende de los streams del Climber y del ciclo de vida del servicio en la Karoo. Para validar:

```bash
adb logcat -c
adb logcat -s PowerClimb
```

Durante una ruta con una subida:

- Debe aparecer `climb active=true ...`.
- Cada 5 segundos debe aparecer `climb signal elevToTop=... distToTop=... climbNumber=... power=...`.
- Al coronar debe aparecer `climb active=false ...` y `phase=FINISHED ...`.
- Al empezar otra subida debe aparecer `phase=ACTIVE` con un `climbNumber` nuevo.

Pruebas mínimas:

1. **Subida normal**: comparar la media mostrada con la potencia media del lap de la subida o con `Last Lap Power`.
2. **Cambio de página**: cambiar a otra página y volver. La media debe seguir subiendo y los logs deben seguir llegando.
3. **Pausa**: pausar la ruta, comprobar que la media se congela, reanudar y comprobar que continúa.
4. **Fin de subida**: coronar y comprobar que aparece `FIN` con el valor final.
5. **Subida nueva**: empezar otra subida y comprobar que la media vuelve a empezar desde cero.
6. **Sin ruta**: comprobar qué hacen los streams nativos si el Climber no tiene una ruta activa. Si no hay señal, el campo debe quedarse en `SIN SUBIDA`.

### Si la media se pierde al cambiar de página

El MVP usa el servicio enlazado de la extensión. Si la Karoo lo destruye al desmontar la página, habrá que añadir un foreground service con notificación persistente, igual que hacen otras extensiones. El `ClimbMonitor` ya está separado de la vista para que ese cambio no afecte al cálculo.

## Cómo se calcula

En cada muestra válida de potencia:

```text
media = suma(potencia_anterior × delta_tiempo) / suma(delta_tiempo)
```

Detalles:

- La primera muestra de potencia después del inicio de subida solo fija el punto de referencia; no se inventa potencia para el hueco anterior.
- La media se publica una vez que hay al menos 1 segundo de datos.
- Si la potencia es `0` porque el ciclista va a rueda o sin pedalear, ese cero cuenta.
- Si el stream de potencia desaparece más de 3 segundos, ese intervalo no entra en el denominador.
- Al pausar la ruta se cierra el intervalo pendiente y se congela la media.
- Al coronar se cierra el intervalo pendiente en ese instante; los vatios posteriores no entran en la media.
- Al reanudar se vuelve a tomar la primera muestra como referencia.

## Estructura

```text
app/src/main/kotlin/com/powerclimb/
├── PowerClimbExtension.kt        Servicio de la extensión y conexión con Karoo
├── ClimbAvgPowerDataType.kt     Data type gráfico y stream numérico
├── data/
│   ├── ClimbMonitor.kt          Suscripción a los streams nativos
│   └── ClimbSessionTracker.kt   Máquina de estados y media ponderada
└── ui/
    └── ClimbPowerRemoteViews.kt Renderizado RemoteViews
```

Tests:

```text
app/src/test/kotlin/com/powerclimb/data/ClimbSessionTrackerTest.kt
```

## Limitaciones conocidas

- No hay fallback por ruta: se confía en los streams del Climber nativo. Si en la prueba de dispositivo faltan eventos, el siguiente paso es añadir una detección de respaldo con `OnNavigationState` y `DISTANCE_TO_DESTINATION`.
- Solo media; NP no está implementada.
- No hay pantalla de configuración ni selección de métrica.
- No hay foreground service todavía; queda pendiente de la prueba de cambio de página.
- El APK es de debug y no está preparado para publicación.

## Licencia

MIT. Consulta [LICENSE](LICENSE).
