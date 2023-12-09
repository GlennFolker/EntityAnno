package ent;

import arc.files.*;
import arc.struct.*;
import arc.util.*;
import arc.util.serialization.*;
import org.gradle.api.*;
import org.gradle.api.file.*;
import org.gradle.api.plugins.*;
import org.gradle.api.tasks.bundling.*;
import org.gradle.api.tasks.compile.*;
import org.jetbrains.kotlin.gradle.internal.*;
import org.jetbrains.kotlin.gradle.plugin.*;

import java.io.*;
import java.util.concurrent.atomic.*;

/**
 * Gradle plugin for creating necessary entity component generation classes.
 * @author GlennFolker
 */
@SuppressWarnings("unused")
public class EntityAnnoPlugin implements Plugin<Project>{
    public static final String defMindustryVersion = "v146";
    public static final int version = 0;

    @Override
    public void apply(Project project){
        var plugins = project.getPlugins();
        var exts = project.getExtensions();
        var tasks = project.getTasks();

        // Apply 'java', 'kotlin-jvm', and 'kotlin-kapt' plugins.
        plugins.apply("java");
        plugins.apply(KotlinPluginWrapper.class);
        plugins.apply(Kapt3GradleSubplugin.class);

        var compileJava = tasks.getAt("compileJava");
        var fetchDir = new Fi(new File(compileJava.getTemporaryDir(), "fetched"));
        var fetchTemp = fetchDir.sibling("fetch.txt");

        // Add the `entityAnno{}` extension
        var ext = exts.create("entityAnno", EntityAnnoExtension.class);
        ext.getMindustryVersion().convention(defMindustryVersion);

        tasks.create("procComps", t -> t.doFirst(tt -> {
            var fetchPackage = ext.getFetchPackage().get();
            for(var fi : fetchDir.list()){
                if(!fi.extEquals("java")) continue;
                fi.writeString(procComp(fi.readString(), fetchPackage), false);
            }
        }));

        var fetchComps = tasks.create("fetchComps", t -> t.doFirst(tt -> {
            project.delete(fetchDir.file());
            fetchDir.mkdirs();

            var amount = new AtomicInteger(0);
            var propVersion = ext.getMindustryVersion().get();

            ObjectSet<UnsafeRunnable> fetches = new ObjectSet<>();
            Http.get("https://api.github.com/repos/Anuken/Mindustry/contents/core/src/mindustry/entities/comp?ref=" + propVersion)
                .timeout(0)
                .error(e -> { throw new RuntimeException(e); })
                .block(res -> {
                    var list = Jval.read(res.getResultAsString()).asArray();
                    amount.set(list.size);

                    var fetchPackage = ext.getFetchPackage().get();
                    var srcDir = fetchDir.child(fetchPackage.replace('.', '/'));
                    srcDir.mkdirs();

                    for(var val : list){
                        var filename = val.get("name").asString();
                        var fileurl = val.get("download_url").asString();

                        fetches.add(() -> Http.get(fileurl)
                            .timeout(0)
                            .error(e -> { throw new RuntimeException(e); })
                            .block(comp -> {
                                var fi = srcDir.child(filename);
                                fi.writeString(procComp(comp.getResultAsString(), fetchPackage), false);

                                amount.decrementAndGet();
                            })
                        );
                    }
                });

            var threads = fetches.toSeq().map(fetch -> new Thread(() -> {
                try{
                    fetch.run();
                }catch(Throwable e){
                    Log.err(e);
                }

                synchronized(tt){
                    fetches.remove(fetch);
                }
            }));

            threads.each(Thread::start);
            while(true){
                if(!fetches.isEmpty()){
                    Thread.yield();
                }else{
                    threads.each(worker -> {
                        try{
                            worker.join();
                        }catch(InterruptedException e){
                            throw new RuntimeException(e);
                        }
                    });
                    break;
                }
            }

            int count = amount.get();
            if(count != 0) throw new IllegalStateException("Couldn't write all components, found " + count + " unwritten.");

            fetchTemp.writeString(propVersion + "/" + version);
        }));

        project.afterEvaluate(p -> {
            // Configure KAPT extension.
            var kaptExt = exts.getByType(KaptExtension.class);
            kaptExt.setKeepJavacAnnotationProcessors(true);

            // Add "${tasks.compileJava.temporaryDir}/fetched/" as Java source set.
            exts.getByType(JavaPluginExtension.class)
                .getSourceSets().getAt("main")
                .getJava().srcDir(fetchDir.file());

            // Exclude fetched and generation source classes.
            for(var task : tasks.withType(Jar.class)){
                task.setDuplicatesStrategy(DuplicatesStrategy.EXCLUDE);
                task.exclude(
                    ext.getFetchPackage().get().replace('.', '/') + "/**",
                    ext.getGenSrcPackage().get().replace('.', '/') + "/**"
                );
            }

            // Fetch components first.
            var propVersion = ext.getMindustryVersion().get();
            for(var task : tasks.withType(JavaCompile.class)){
                if(!fetchDir.exists() || !fetchTemp.exists()){
                    task.dependsOn(fetchComps);
                    task.mustRunAfter(fetchComps);
                }else{
                    var content = fetchTemp.readString().split("/");
                    var ver = content[0].strip();
                    var rev = content[1].strip();

                    if(!ver.equals(propVersion) || !rev.equals(String.valueOf(version))){
                        task.dependsOn(fetchComps);
                        task.mustRunAfter(fetchComps);
                    }
                }
            }

            // Add annotation processor options.
            kaptExt.arguments(args -> {
                args.arg("modName", ext.getModName().get());
                args.arg("genPackage", ext.getGenPackage().get());
                args.arg("fetchPackage", ext.getFetchPackage().get());
                args.arg("revisionDir", ext.getRevisionDir().get().getAbsolutePath());
                return null;
            });

            // Prevent running these tasks to speed up compile-time.
            for(var task : new String[]{
                "checkKotlinGradlePluginConfigurationErrors",
                "kaptGenerateStubsKotlin",
                "compileKotlin",
            }) {
                tasks.getByName(task, t -> {
                    t.onlyIf(spec -> false);
                    t.getOutputs().upToDateWhen(spec -> true);
                });
            }
        });
    }

    public static String procComp(String source, String fetchPackage){
        return source
            .replace("mindustry.entities.comp", fetchPackage)
            .replace("mindustry.annotations.Annotations.*", "ent.anno.Annotations.*")
            .replaceAll("@Component\\((base = true|.)+\\)\n*", "@EntityComponent(base = true, vanilla = true)\n")
            .replaceAll("@Component\n*", "@EntityComponent(vanilla = true)\n")
            .replaceAll("@BaseComponent\n*", "@EntityBaseComponent\n")
            .replaceAll("@CallSuper\n*", "")
            .replaceAll("@Final\n*", "")
            .replaceAll("@EntityDef\\(*.*\\)*\n*", "");
    }
}
