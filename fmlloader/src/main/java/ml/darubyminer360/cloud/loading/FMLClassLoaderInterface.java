package ml.darubyminer360.cloud.loading;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.jar.Manifest;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.impl.game.GameProvider;

public interface FMLClassLoaderInterface {
    @SuppressWarnings("resource")
    static FMLClassLoaderInterface create(boolean useCompatibility, boolean isDevelopment, EnvType envType, GameProvider provider) {
        if (useCompatibility) {
            return new FMLCompatibilityClassLoader(isDevelopment, envType, provider).getDelegate();
        } else {
            return new FMLClassLoader(isDevelopment, envType, provider).getDelegate();
        }
    }

    void initializeTransformers();

    ClassLoader getClassLoader();

    void addCodeSource(Path path);
    void setAllowedPrefixes(Path codeSource, String... prefixes);
    void setValidParentClassPath(Collection<Path> codeSources);

    Manifest getManifest(Path codeSource);

    boolean isClassLoaded(String name);
    Class<?> loadIntoTarget(String name) throws ClassNotFoundException;

    byte[] getRawClassBytes(String name) throws IOException;
    byte[] getPreMixinClassBytes(String name);
}
