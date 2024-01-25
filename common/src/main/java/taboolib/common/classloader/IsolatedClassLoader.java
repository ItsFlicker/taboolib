package taboolib.common.classloader;

import taboolib.common.PrimitiveSettings;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collection;
import java.util.HashSet;
import java.util.ServiceLoader;
import java.util.Set;

public class IsolatedClassLoader extends URLClassLoader {

    /**
     * 当前项目正在使用的加载器
     * 由 "插件主类" 初始化
     */
    public static IsolatedClassLoader INSTANCE;

    private final Set<String> excludedClasses = new HashSet<>();
    private final Set<String> excludedPackages = new HashSet<>();

    public static void init(Class<?> clazz) {
        // 初始化隔离类加载器
        INSTANCE = new IsolatedClassLoader(clazz);
        // 加载启动类
        try {
            Class<?> delegateClass = Class.forName("taboolib.common.PrimitiveLoader", true, INSTANCE);
            Object delegateObject = delegateClass.getConstructor().newInstance();
            delegateClass.getMethod("init").invoke(delegateObject);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public IsolatedClassLoader(Class<?> clazz) {
        this(new URL[]{clazz.getProtectionDomain().getCodeSource().getLocation()}, clazz.getClassLoader());
        excludedClasses.add(clazz.getName());
    }

    public IsolatedClassLoader(URL[] urls, ClassLoader parent) {
        super(urls, parent);

        // 默认排除类
        excludedClasses.add("taboolib.common.classloader.IsolatedClassLoader");
        excludedClasses.add("taboolib.common.platform.Plugin");
        excludedPackages.add("java.");

        // 在非隔离模式下排除 ClassAppender 和 TabooLib
        // 因为这俩玩意需要缓存数据
        if (!PrimitiveSettings.IS_ISOLATED_MODE) {
            excludedClasses.add("taboolib.common.ClassAppender");
            excludedClasses.add("taboolib.common.TabooLib");
        }

        // Load excluded classes and packages by SPI
        ServiceLoader<IsolatedClassLoaderConfig> serviceLoader = ServiceLoader.load(IsolatedClassLoaderConfig.class, parent);
        for (IsolatedClassLoaderConfig config : serviceLoader) {
            Set<String> configExcludedClasses = config.excludedClasses();
            if (configExcludedClasses != null && !configExcludedClasses.isEmpty()) {
                excludedClasses.addAll(configExcludedClasses);
            }
            Set<String> configExcludedPackages = config.excludedPackages();
            if (configExcludedPackages != null && !configExcludedPackages.isEmpty()) {
                excludedPackages.addAll(configExcludedPackages);
            }
        }
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        return loadClass(name, resolve, true);
    }

    public Class<?> loadClass(String name, boolean resolve, boolean checkParents) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            Class<?> findClass = findLoadedClass(name);
            // Check isolated classes and libraries before parent to:
            //   - prevent accessing classes of other plugins
            //   - prevent the usage of old patch classes (which stay in memory after reloading)
            if (findClass == null && !excludedClasses.contains(name)) {
                boolean flag = true;
                for (String excludedPackage : excludedPackages) {
                    if (name.startsWith(excludedPackage)) {
                        flag = false;
                        break;
                    }
                }
                if (flag) {
                    findClass = findClassOrNull(name);
                }
            }
            if (findClass == null && checkParents) {
                findClass = loadClassFromParentOrNull(name);
            }
            if (findClass == null) {
                throw new ClassNotFoundException(name);
            }
            if (resolve) {
                resolveClass(findClass);
            }
            return findClass;
        }
    }

    private Class<?> findClassOrNull(String name) {
        try {
            return findClass(name);
        } catch (ClassNotFoundException ignored) {
            return null;
        }
    }

    private Class<?> loadClassFromParentOrNull(String name) {
        try {
            return getParent().loadClass(name);
        } catch (ClassNotFoundException ignored) {
            return null;
        }
    }

    public void addExcludedClass(String name) {
        excludedClasses.add(name);
    }

    public void addExcludedClasses(Collection<String> names) {
        excludedClasses.addAll(names);
    }

    public void addExcludedPackage(String name) {
        excludedPackages.add(name);
    }

    public void addExcludedPackages(Collection<String> names) {
        excludedPackages.addAll(names);
    }
}

