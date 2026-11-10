# PowerClimb

Dos campos de datos gráficos para la Hammerhead Karoo 3 que muestran la **potencia media ponderada por tiempo de la subida activa del Climber nativo**:

- **Climb Power** / `Pot. Subida`: número grande alineado a la derecha.
- **Climb Power Zones** / `Pot. Subida Zonas`: el mismo número sobre el color de la zona de potencia media.

La presentación es minimalista y transparente, con tipografías y colores nativos. En el modo numérico el color del texto y del icono se adapta al tema claro u oscuro de la Karoo. El modo de zonas usa el color de la zona como fondo y elige texto negro o blanco para mantener el contraste.

En ambos modos el icono va a la izquierda y la etiqueta a la derecha del campo. El número usa el tamaño nativo del hueco (`ViewConfig.textSize`), se alinea a la derecha y abajo como los campos numéricos de la Karoo y ocupa todo el ancho del campo. Solo se reduce si un valor de cuatro o más dígitos no cabe en el ancho disponible.

## Instalar desde Releases

Cada push a `main` que toque código de la app (`app/**`, Gradle o el workflow de release) compila, firma y publica `powerclimb.apk` en [Releases](../../releases/latest). Los cambios solo de documentación no generan release.

1. Descarga [powerclimb.apk](../../releases/latest/download/powerclimb.apk) desde la última release.
2. Sideload a la Karoo por USB/ADB:

```bash
adb install powerclimb.apk
```

Si ya tienes instalada una build debug anterior con otra firma, desinstálala una vez antes de instalar la release:

```bash
adb uninstall com.powerclimb
```

La URL directa al último APK es:

```text
https://github.com/<usuario>/<repo>/releases/latest/download/powerclimb.apk
```

El versionado es automático: cada release parte del último tag `vX.Y.Z` y sube el patch (`v0.3.0` → `v0.3.1` → `v0.3.2`…). El `versionCode` del APK se calcula como `major * 10000 + minor * 100 + patch`, así que siempre crece y el sideload actualiza bien sin desinstalar. No hace falta tocar `app/build.gradle.kts` para publicar; para un cambio de minor o major, crea el tag a mano (`git tag v0.4.0 && git push origin v0.4.0`) antes del siguiente push.

En CI la versión es obligatoria: si el workflow no inyecta `powerclimb.versionName`/`powerclimb.versionCode`, el build falla en vez de publicar una versión por defecto. Los builds locales usan `powerclimb.baseVersion` de `gradle.properties` y se marcan como `X.Y.Z-local`.

Las builds de CI se firman con la clave pública `ci/powerclimb-ci.jks` para que las actualizaciones sean instalables. Se puede sustituir por una clave privada configurando los secrets indicados en [`ci/README.md`](ci/README.md).

## Base técnica

PowerClimb usa la librería oficial de extensiones de Karoo:

- Repositorio: [hammerheadnav/karoo-ext](https://github.com/hammerheadnav/karoo-ext)
- Dependencia: `com.github.hammerheadnav:karoo-ext:1.1.9`

No es un fork del repositorio: es una app independiente construida sobre su API de extensiones, data types, streams y `RemoteViews`.

## Qué hace

- Detecta el inicio y el fin de una subida usando los streams nativos del Climber:
  - `ELEVATION_TO_TOP`
  - `DISTANCE_TO_TOP`
  - `CLIMB_NUMBER`
- Mantiene la media acumulada aunque cambies de página mientras la subida sigue activa. El tracker vive en el servicio de la extensión, no en la vista.
- Al coronar, congela la media final y **la conserva** después de terminar la subida o la ruta.
- Reinicia el valor al empezar la siguiente subida o una ruta nueva.
- Muestra `--` únicamente cuando no hay ningún valor que mostrar. La fuente
  numérica nativa (`Relative`) no incluye el guion largo; dos guiones reproducen
  el mismo hueco visual y coinciden con los placeholders de los campos nativos.
- No muestra tiempo, potencia actual, unidad `W`, insignia de subida ni línea secundaria. El reloj real se mantiene internamente para los futuros campos de tiempo, NP, pulso y cadencia.

## Las dos variantes

### Numérica

- Etiqueta `C.POWER` con icono de rayo y montaña.
- Media en vatios, alineada a la derecha.
- Fondo transparente y texto/icono según el tema.

### Zonas

- Fondo del color de la zona de la media.
- Texto e icono en negro o blanco según el contraste del color.
- Sin zonas configuradas no se inventa ningún color: el fondo queda transparente.

Los límites y el número de zonas se leen de `UserProfile.powerZones` del dispositivo. No hay límites hardcodeados ni un fallback por porcentaje de FTP. La paleta es la nativa de Karoo: Z1 `#60EEB2`, Z2 `#00B988`, Z3 `#FFF500`, Z4 `#FB8C65`, Z5 `#FE581F`, Z6 `#D60404`, Z7 `#B700A2`.

## Estado del proyecto

Versión `0.3.0`.

- Media **ponderada por tiempo**.
- Los ceros de potencia cuentan mientras haya señal y la ruta no esté pausada.
- Los huecos de potencia de más de 3 segundos se excluyen de la media.
- Al detectar el coronamiento se congela la media inmediatamente. El final se confirma tras 2 segundos de señal explícita a cero, o tras 5 segundos si el stream deja de emitir.
- La pausa de la ruta congela la media y el reloj interno; al reanudar continúa.
- La media final permanece visible hasta la siguiente subida o la siguiente ruta.
- La normalizada (NP) queda para una versión posterior. Añadirla requiere un buffer móvil o un acumulador de cuarta potencia; el tracker actual solo guarda vatios·segundo, segundos y el reloj interno de la subida.

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

## Instalar una build local

Para probar cambios locales, compila el APK de debug e instálalo con la Karoo conectada por USB y la depuración ADB activada:

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Después, en la Karoo:

1. Abre los ajustes de perfiles/páginas.
2. Edita una página y añade un campo de datos.
3. Busca `PowerClimb` y elige una de las dos variantes.
4. Ajusta el tamaño del campo. El diseño se adapta a 1x1, 1x2, 2x1 y 2x2.

No hay app de ajustes: la variante se elige en el selector de campos de datos de la Karoo.

Para generar un APK release firmado localmente:

```bash
./gradlew :app:assembleRelease
# app/build/outputs/apk/release/app-release.apk
```

La firma de release usa `ci/powerclimb-ci.jks` por defecto. Para usar una clave privada, exporta `RELEASE_KEYSTORE_PATH`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS` y `RELEASE_KEY_PASSWORD` antes de compilar.

## Validación en dispositivo

La lógica está cubierta por tests unitarios, pero el comportamiento real depende de los streams del Climber y del ciclo de vida del servicio. Para validar:

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

1. **Media real**: comparar la media mostrada con la potencia media del lap de la subida o con `Last Lap Power`.
2. **Dos variantes**: añadir los dos campos y comprobar número y fondo de zona.
3. **Tamaños**: comprobar 1x1, 1x2, 2x1 y 2x2; la etiqueta debe quedar a la derecha y el número no debe recortarse.
4. **Tema claro/oscuro**: en numérico el texto y el icono deben invertirse; en zonas debe mantenerse el contraste sobre el color.
5. **Cambio de página**: cambiar a otra página y volver. La media debe seguir subiendo y los logs deben seguir llegando.
6. **Pausa**: pausar la ruta, comprobar que la media se congela, reanudar y comprobar que continúa.
7. **Fin de subida**: coronar y comprobar que el valor final se queda visible, sin etiqueta `FIN`.
8. **Subida nueva**: empezar otra subida y comprobar que la media vuelve a empezar desde cero.
9. **Ruta nueva**: terminar la ruta, comprobar que el último valor sigue visible y que al iniciar otra ruta aparece `--`.
10. **Idioma**: con la Karoo en español deben aparecer `C.POWER` y `Pot. Subida Zonas`; en inglés, `C.POWER` y `Climb Power Zones`.
11. **Sin zonas**: probar un perfil sin zonas de potencia; no debe aparecer ningún color inventado.

La pantalla nativa del Climber solo se abre en una subida real. La simulación sirve para validar los estados en una página de datos normal.

### Simulación de subida (solo build debug)

Para validar los estados `ACTIVE`, pausa, final y reset sin una subida real, el build debug incluye un receptor de broadcasts que inyecta datos falsos en el mismo `ClimbSessionTracker` que usan los streams nativos:

```bash
adb shell am broadcast -a com.powerclimb.DEBUG_CLIMB -p com.powerclimb --es action start
adb shell am broadcast -a com.powerclimb.DEBUG_CLIMB -p com.powerclimb --es action pause
adb shell am broadcast -a com.powerclimb.DEBUG_CLIMB -p com.powerclimb --es action resume
adb shell am broadcast -a com.powerclimb.DEBUG_CLIMB -p com.powerclimb --es action finish
adb shell am broadcast -a com.powerclimb.DEBUG_CLIMB -p com.powerclimb --es action reset
```

El simulador:

- Marca una subida ficticia con `ELEVATION_TO_TOP=350`, `DISTANCE_TO_TOP=3500` y `CLIMB_NUMBER=1`.
- Genera potencia a 1 Hz con una rampa de 200 W a 257 W, en pasos de 3 W.
- Pausa y reanuda igual que una ruta real, congelando la media.
- Al terminar envía una señal explícita a cero; el estado final aparece tras unos 2 segundos y el valor se conserva.
- `reset` vuelve a `--` y libera los streams nativos.
- Mientras está activo ignora los streams nativos para que no mezclen datos. Unos 3 segundos después de `finish` vuelve a escucharlos.

El receptor solo se declara en `src/debug/AndroidManifest.xml`, así que no existe en un build release. Para ver el campo durante la simulación, ponlo también en una página de datos normal (por ejemplo, la página 2): la pantalla nativa del Climber no se abre con datos simulados porque la controla Karoo al detectar una subida real.

Los eventos del simulador aparecen en el log:

```bash
adb logcat -s PowerClimb
```

Busca líneas `DEBUG climb started`, `DEBUG tick=...`, `DEBUG ride paused`, `DEBUG climb finish requested` y `phase=FINISHED ...`.

### Si la media se pierde al cambiar de página

El proyecto usa el servicio enlazado de la extensión. Si la Karoo lo destruye al desmontar la página, habrá que añadir un foreground service con notificación persistente, igual que hacen otras extensiones. El `ClimbMonitor` ya está separado de la vista para que ese cambio no afecte al cálculo. En la prueba en dispositivo el servicio sobrevivió al cambio de página; falta confirmarlo en una salida larga con la pantalla apagada o la app en segundo plano.

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
- El reloj interno excluye las pausas y se conserva al terminar; no se muestra en esta versión.

## Estructura

```text
app/src/main/kotlin/com/powerclimb/
├── PowerClimbExtension.kt        Servicio de la extensión y conexión con Karoo
├── ClimbAvgPowerDataType.kt     Data type gráfico compartido por las dos variantes
├── data/
│   ├── ClimbMonitor.kt          Streams nativos, estado de ruta y perfil
│   ├── ClimbSessionTracker.kt   Máquina de estados, media y reloj interno
│   └── PowerZoneProfile.kt      Adaptación de las zonas del dispositivo
├── debug/
│   ├── DebugClimbBridge.kt      Puente entre el receptor debug y el monitor
│   └── DebugClimbReceiver.kt    Receptor del simulador (solo build debug)
└── ui/
    ├── ClimbFieldMode.kt        Modos numérico y zonas
    ├── ClimbPowerRemoteViews.kt Renderizado RemoteViews
    └── PowerZonePalette.kt      Paleta y contraste nativos

app/src/main/res/values/strings.xml       Inglés por defecto
app/src/main/res/values-es/strings.xml    Español
app/src/debug/AndroidManifest.xml         Receptor del simulador
```

Tests:

```text
app/src/test/kotlin/com/powerclimb/data/ClimbSessionTrackerTest.kt
app/src/test/kotlin/com/powerclimb/data/PowerZoneProfileTest.kt
```

## Limitaciones conocidas

- No hay fallback por ruta: se confía en los streams del Climber nativo. Si en la prueba de dispositivo faltan eventos, el siguiente paso es añadir una detección de respaldo con `OnNavigationState` y `DISTANCE_TO_DESTINATION`.
- Solo media; NP, tiempo, pulso y cadencia no están implementados todavía como campos independientes.
- No hay pantalla de configuración; la variante se elige en el selector de campos de la Karoo.
- No hay foreground service: la prueba de cambio de página pasó con el servicio enlazado, pero falta validarlo en una salida larga con la pantalla apagada o la app en segundo plano.
- El simulador no puede abrir la pantalla nativa del Climber; para validar visualmente el campo en esa pantalla hace falta una subida real. La simulación se prueba en una página de datos normal.
- El APK es de debug y no está preparado para publicación. El receptor del simulador solo se declara en el build debug.

## Licencia

MIT. Consulta [LICENSE](LICENSE).
