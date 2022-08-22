package ml.cloudmc.cloudloader.loading;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.CodeSource;
import java.security.SecureClassLoader;
import java.util.Enumeration;
import java.util.Objects;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.impl.game.GameProvider;
import net.fabricmc.loader.impl.launch.knot.DummyClassLoader;

// class name referenced by string constant in net.fabricmc.loader.impl.util.LoaderUtil.verifyNotInTargetCl
//final class FMLClassLoader extends SecureClassLoader implements FMLClassDelegate.ClassLoaderAccess {
final class FMLClassLoader extends SecureClassLoader implements FMLClassDelegate.ClassLoaderAccess {
    private static final class DynamicURLClassLoader extends URLClassLoader {
//        private DynamicURLClassLoader(URL[] urls) {
        private DynamicURLClassLoader(URL[] urls, ClassLoader parent) {
//            super(urls, new DummyClassLoader());
            super(urls, parent);
        }

        @Override
        public void addURL(URL url) {
            super.addURL(url);
        }

        static {
            registerAsParallelCapable();
        }
    }

    private final FMLClassLoader.DynamicURLClassLoader urlLoader;
    private final ClassLoader originalLoader;
    private final FMLClassDelegate<FMLClassLoader> delegate;

    FMLClassLoader(boolean isDevelopment, EnvType envType, GameProvider provider) {
//        super(new FMLClassLoader.DynamicURLClassLoader(new URL[0], FMLClassLoader.class.getClassLoader()));
        super(FMLClassLoader.class.getClassLoader());
        this.originalLoader = getClass().getClassLoader();
//        this.urlLoader = (FMLClassLoader.DynamicURLClassLoader) getParent();
        this.urlLoader = new FMLClassLoader.DynamicURLClassLoader(new URL[0], FMLClassLoader.class.getClassLoader());
        this.delegate = new FMLClassDelegate<>(isDevelopment, envType, this, originalLoader, provider);
    }

    FMLClassDelegate<?> getDelegate() {
        return delegate;
    }

    @Override
    public URL getResource(String name) {
        Objects.requireNonNull(name);

        URL url = urlLoader.getResource(name);

        if (url == null) {
            url = originalLoader.getResource(name);
        }

        return url;
//        return getParent().getResource(name);
    }

    @Override
    public URL findResource(String name) {
        Objects.requireNonNull(name);

        return urlLoader.findResource(name);
//        return getParent().findResource(name);
    }

    @Override
    public InputStream getResourceAsStream(String name) {
        Objects.requireNonNull(name);

        InputStream inputStream = urlLoader.getResourceAsStream(name);

        if (inputStream == null) {
            inputStream = originalLoader.getResourceAsStream(name);
        }

        return inputStream;
    }

    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
        Objects.requireNonNull(name);

        Enumeration<URL> first = urlLoader.getResources(name);
        Enumeration<URL> second = originalLoader.getResources(name);
        return new Enumeration<URL>() {
            Enumeration<URL> current = first;

            @Override
            public boolean hasMoreElements() {
                if (current == null) {
                    return false;
                }

                if (current.hasMoreElements()) {
                    return true;
                }

                if (current == first && second.hasMoreElements()) {
                    return true;
                }

                return false;
            }

            @Override
            public URL nextElement() {
                if (current == null) {
                    return null;
                }

                if (!current.hasMoreElements()) {
                    if (current == first) {
                        current = second;
                    } else {
                        current = null;
                        return null;
                    }
                }

                return current.nextElement();
            }
        };
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        return delegate.loadClass(name, resolve);
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        return delegate.tryLoadClass(name, false);
    }

    @Override
    public void addUrlFwd(URL url) {
        urlLoader.addURL(url);
    }

    @Override
    public URL findResourceFwd(String name) {
        return urlLoader.findResource(name);
    }

    @Override
    public Package getPackageFwd(String name) {
        return super.getPackage(name);
    }

    @Override
    public Package definePackageFwd(String name, String specTitle, String specVersion, String specVendor,
                                    String implTitle, String implVersion, String implVendor, URL sealBase) throws IllegalArgumentException {
        return super.definePackage(name, specTitle, specVersion, specVendor, implTitle, implVersion, implVendor, sealBase);
    }

    @Override
    public Object getClassLoadingLockFwd(String name) {
        return super.getClassLoadingLock(name);
    }

    @Override
    public Class<?> findLoadedClassFwd(String name) {
        return super.findLoadedClass(name);
    }

    @Override
    public Class<?> defineClassFwd(String name, byte[] b, int off, int len, CodeSource cs) {
        return super.defineClass(name, b, off, len, cs);
    }

    @Override
    public void resolveClassFwd(Class<?> cls) {
        super.resolveClass(cls);
    }

    static {
        registerAsParallelCapable();
    }
}
