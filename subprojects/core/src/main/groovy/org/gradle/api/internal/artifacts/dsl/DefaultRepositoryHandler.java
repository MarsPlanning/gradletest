/*
 * Copyright 2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.api.internal.artifacts.dsl;

import groovy.lang.Closure;
import org.apache.ivy.plugins.resolver.AbstractResolver;
import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.apache.ivy.plugins.resolver.FileSystemResolver;
import org.gradle.api.Action;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.dsl.*;
import org.gradle.api.artifacts.maven.GroovyMavenDeployer;
import org.gradle.api.artifacts.maven.MavenResolver;
import org.gradle.api.internal.Instantiator;
import org.gradle.api.internal.artifacts.DefaultResolverContainer;
import org.gradle.api.internal.artifacts.ResolverFactory;
import org.gradle.api.internal.artifacts.repositories.ArtifactRepositoryInternal;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.util.ConfigureUtil;
import org.gradle.util.GUtil;
import org.gradle.util.WrapUtil;

import java.util.*;

/**
 * @author Hans Dockter
 */
public class DefaultRepositoryHandler extends DefaultResolverContainer implements RepositoryHandler {
    private final Set<String> repositoryNames = new HashSet<String>();

    public DefaultRepositoryHandler(ResolverFactory resolverFactory, FileResolver fileResolver, Instantiator instantiator) {
        super(resolverFactory, fileResolver, instantiator);
    }

    public FlatDirectoryArtifactRepository flatDir(Action<? super FlatDirectoryArtifactRepository> action) {
        return addRepository(getResolverFactory().createFlatDirRepository(), action, "flatDir");
    }

    public FlatDirectoryArtifactRepository flatDir(Closure configureClosure) {
        return addRepository(getResolverFactory().createFlatDirRepository(), configureClosure, "flatDir");
    }

    public FileSystemResolver flatDir(Map<String, ?> args) {
        Map<String, Object> modifiedArgs = new HashMap<String, Object>(args);
        if (modifiedArgs.containsKey("dirs")) {
            Object value = modifiedArgs.get("dirs");
            if (!(value instanceof Iterable<?>)) {
                modifiedArgs.put("dirs", Collections.singletonList(value));
            }
        }
        FlatDirectoryArtifactRepository repository = addRepository(getResolverFactory().createFlatDirRepository(), modifiedArgs, "flatDir");
        List<DependencyResolver> resolvers = new ArrayList<DependencyResolver>();
        ((ArtifactRepositoryInternal) repository).createResolvers(resolvers);
        assert resolvers.size() == 1;
        return (FileSystemResolver) resolvers.get(0);
    }

    private String getNameFromMap(Map args, String defaultName) {
        Object name = args.get("name");
        return name != null ? name.toString() : defaultName;
    }

    private List<Object> createListFromMapArg(Map args, String argName) {
        Object dirs = args.get(argName);
        if (dirs == null) {
            return Collections.emptyList();
        }
        Iterable<Object> iterable;
        if (dirs instanceof Iterable) {
            iterable = (Iterable<Object>) dirs;
        } else {
            iterable = WrapUtil.toSet(dirs);
        }
        List<Object> list = new ArrayList<Object>();
        for (Object o : iterable) {
            list.add(o);
        }
        return list;
    }

    public DependencyResolver mavenCentral() {
        return mavenCentral(Collections.emptyMap());
    }

    public DependencyResolver mavenCentral(Map args) {
        List<Object> urls = createListFromMapArg(args, "urls");
        return addLast(getResolverFactory().createMavenRepoResolver(
                getNameFromMap(args, DEFAULT_MAVEN_CENTRAL_REPO_NAME),
                MAVEN_CENTRAL_URL,
                urls.toArray()));
    }

    public DependencyResolver mavenLocal() {
        return addLast(getResolverFactory().createMavenLocalResolver(DEFAULT_MAVEN_LOCAL_REPO_NAME));
    }

    public DependencyResolver mavenRepo(Map args) {
        return mavenRepo(args, null);
    }

    public DependencyResolver mavenRepo(Map args, Closure configClosure) {
        List<Object> urls = createListFromMapArg(args, "urls");
        if (urls.isEmpty()) {
            throw new InvalidUserDataException("You must specify the urls for a Maven repo.");
        }
        List<Object> extraUrls = urls.subList(1, urls.size());
        AbstractResolver resolver = getResolverFactory().createMavenRepoResolver(
                getNameFromMap(args, urls.get(0).toString()),
                urls.get(0),
                extraUrls.toArray());
        return addLast(resolver, configClosure);
    }

    public GroovyMavenDeployer mavenDeployer(Map args) {
        GroovyMavenDeployer mavenDeployer = createMavenDeployer(args);
        return (GroovyMavenDeployer) addLast(mavenDeployer);
    }

    private GroovyMavenDeployer createMavenDeployer(Map args) {
        GroovyMavenDeployer mavenDeployer = createMavenDeployer("dummyName");
        String defaultName = RepositoryHandler.DEFAULT_MAVEN_DEPLOYER_NAME + "-" + System.identityHashCode(
                mavenDeployer);
        mavenDeployer.setName(getNameFromMap(args, defaultName));
        return mavenDeployer;
    }

    public GroovyMavenDeployer mavenDeployer() {
        return mavenDeployer(Collections.emptyMap());
    }

    public GroovyMavenDeployer mavenDeployer(Closure configureClosure) {
        return mavenDeployer(Collections.emptyMap(), configureClosure);
    }

    public GroovyMavenDeployer mavenDeployer(Map args, Closure configureClosure) {
        GroovyMavenDeployer mavenDeployer = createMavenDeployer(args);
        return (GroovyMavenDeployer) addLast(mavenDeployer, configureClosure);
    }

    public MavenResolver mavenInstaller() {
        return mavenInstaller(Collections.emptyMap());
    }

    public MavenResolver mavenInstaller(Closure configureClosure) {
        return mavenInstaller(Collections.emptyMap(), configureClosure);
    }

    public MavenResolver mavenInstaller(Map args) {
        MavenResolver mavenInstaller = createMavenInstaller(args);
        return (MavenResolver) addLast(mavenInstaller);
    }

    public MavenResolver mavenInstaller(Map args, Closure configureClosure) {
        MavenResolver mavenInstaller = createMavenInstaller(args);
        return (MavenResolver) addLast(mavenInstaller, configureClosure);
    }

    private MavenResolver createMavenInstaller(Map args) {
        MavenResolver mavenInstaller = createMavenInstaller("dummyName");
        String defaultName = RepositoryHandler.DEFAULT_MAVEN_INSTALLER_NAME + "-" + System.identityHashCode(
                mavenInstaller);
        mavenInstaller.setName(getNameFromMap(args, defaultName));
        return mavenInstaller;
    }

    public MavenArtifactRepository maven(Action<? super MavenArtifactRepository> action) {
        return addRepository(getResolverFactory().createMavenRepository(), action, "maven");
    }

    public MavenArtifactRepository maven(Closure closure) {
        return addRepository(getResolverFactory().createMavenRepository(), closure, "maven");
    }

    public IvyArtifactRepository ivy(Action<? super IvyArtifactRepository> action) {
        return addRepository(getResolverFactory().createIvyRepository(), action, "ivy");
    }

    public IvyArtifactRepository ivy(Closure closure) {
        return addRepository(getResolverFactory().createIvyRepository(), closure, "ivy");
    }

    private <T extends ArtifactRepository> T addRepository(T repository, Action<? super T> action, String defaultName) {
        action.execute(repository);
        addRepository(repository, defaultName);
        return repository;
    }

    private <T extends ArtifactRepository> T addRepository(T repository, Closure closure, String defaultName) {
        ConfigureUtil.configure(closure, repository);
        addRepository(repository, defaultName);
        return repository;
    }

    private <T extends ArtifactRepository> T addRepository(T repository, Map<String, ?> args, String defaultName) {
        ConfigureUtil.configureByMap(args, repository);
        addRepository(repository, defaultName);
        return repository;
    }

    private void addRepository(ArtifactRepository repository, String defaultName) {
        String repositoryName = repository.getName();
        if (!GUtil.isTrue(repositoryName)) {
            repositoryName = findName(defaultName);
            repository.setName(repositoryName);
        }
        repositoryNames.add(repositoryName);

        List<DependencyResolver> resolvers = new ArrayList<DependencyResolver>();
        ArtifactRepositoryInternal internalRepository = (ArtifactRepositoryInternal) repository;
        internalRepository.createResolvers(resolvers);
        for (DependencyResolver resolver : resolvers) {
            addLast(resolver);
        }
    }

    private String findName(String defaultName) {
        if (!repositoryNames.contains(defaultName)) {
            return defaultName;
        }
        for (int index = 2; true; index++) {
            String candidate = String.format("%s%d", defaultName, index);
            if (!repositoryNames.contains(candidate)) {
                return candidate;
            }
        }
    }
}
