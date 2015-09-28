/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * @test
 * @summary Test StripDebugPlugin
 * @author Jean-Francois Denise
 * @library ../../lib
 * @build tests.*
 * @modules java.base/jdk.internal.jimage
 *          jdk.jlink/jdk.tools.jlink.internal
 *          jdk.jlink/jdk.tools.jlink.internal.plugins
 *          jdk.jlink/jdk.tools.jlink
 *          jdk.jlink/jdk.tools.jimage
 *          jdk.jlink/jdk.tools.jmod
 *          jdk.jdeps/com.sun.tools.classfile
 * @run main StripDebugPluginTest
 */

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;

import com.sun.tools.classfile.Attribute;
import com.sun.tools.classfile.ClassFile;
import com.sun.tools.classfile.Code_attribute;
import com.sun.tools.classfile.ConstantPoolException;
import com.sun.tools.classfile.Method;
import java.util.HashMap;
import java.util.Map;
import jdk.tools.jlink.internal.ImagePluginConfiguration;
import jdk.tools.jlink.internal.ResourcePoolImpl;
import jdk.tools.jlink.internal.plugins.StripDebugProvider;
import jdk.tools.jlink.plugins.CmdPluginProvider;
import jdk.tools.jlink.plugins.ResourcePlugin;
import jdk.tools.jlink.plugins.ResourcePool;
import jdk.tools.jlink.plugins.ResourcePool.Resource;
import jdk.tools.jlink.plugins.StringTable;
import tests.Helper;

public class StripDebugPluginTest {
    public static void main(String[] args) throws Exception {
        new StripDebugPluginTest().test();
    }

    public void test() throws Exception {
        // JPRT not yet ready for jmods
        Helper helper = Helper.newHelper();
        if (helper == null) {
            System.err.println("Test not run, NO jmods directory");
            return;
        }

        List<String> classes = Arrays.asList("toto.Main", "toto.com.foo.bar.X");
        Path moduleFile = helper.generateModuleCompiledClasses(
                helper.getJmodSrcDir(), helper.getJmodClassesDir(), "leaf1", classes);
        Path moduleInfo = moduleFile.resolve("module-info.class");

        // Classes have been compiled in debug.
        List<Path> covered = new ArrayList<>();
        byte[] infoContent = Files.readAllBytes(moduleInfo);
        try (Stream<Path> stream = Files.walk(moduleFile)) {
            for (Iterator<Path> iterator = stream.iterator(); iterator.hasNext(); ) {
                Path p = iterator.next();
                if (Files.isRegularFile(p) && p.toString().endsWith(".class")) {
                    byte[] content = Files.readAllBytes(p);
                    String path = "/" + helper.getJmodClassesDir().relativize(p).toString();
                    String moduleInfoPath = path + "/module-info.class";
                    check(path, content, moduleInfoPath, infoContent);
                    covered.add(p);
                }
            }
        }
        if (covered.isEmpty()) {
            throw new AssertionError("No class to compress");
        } else {
            System.err.println("removed debug attributes from "
                    + covered.size() + " classes");
        }
    }

    private void check(String path, byte[] content, String infoPath, byte[] moduleInfo) throws Exception {
        StripDebugProvider prov = new StripDebugProvider();
        Map<String, Object> options = new HashMap<>();
        options.put(CmdPluginProvider.TOOL_ARGUMENT_PROPERTY,
                ImagePluginConfiguration.ON_ARGUMENT);
        ResourcePlugin debug = (ResourcePlugin) prov.newPlugins(options)[0];
        Resource result1 = stripDebug(debug, new Resource(path, ByteBuffer.wrap(content)), path, infoPath, moduleInfo);

        if (!path.endsWith("module-info.class")) {
            if (result1.getLength() >= content.length) {
                throw new AssertionError("Class size not reduced, debug info not "
                        + "removed for " + path);
            }
            checkDebugAttributes(result1.getByteArray());
        }

        Resource result2 = stripDebug(debug, result1, path, infoPath, moduleInfo);
        if (result1.getLength() != result2.getLength()) {
            throw new AssertionError("removing debug info twice reduces class size of "
                    + path);
        }
        checkDebugAttributes(result1.getByteArray());
    }

    private Resource stripDebug(ResourcePlugin debug, Resource classResource, String path, String infoPath, byte[] moduleInfo) throws Exception {
        ResourcePool resources = new ResourcePoolImpl(ByteOrder.nativeOrder());
        resources.addResource(classResource);
        if (!path.endsWith("module-info.class")) {
            Resource res2 = new Resource(infoPath, ByteBuffer.wrap(moduleInfo));
            resources.addResource(res2);
        }
        ResourcePool results = new ResourcePoolImpl(resources.getByteOrder());
        debug.visit(resources, results, new StringTable() {
            @Override
            public int addString(String str) {
                return -1;
            }

            @Override
            public String getString(int id) {
                throw new UnsupportedOperationException("Not supported yet.");
            }
        });
        System.out.println(classResource.getPath());
        return results.getResource(classResource.getPath());
    }

    private void checkDebugAttributes(byte[] strippedClassFile) throws IOException, ConstantPoolException {
        ClassFile classFile = ClassFile.read(new ByteArrayInputStream(strippedClassFile));
        String[] debugAttributes = new String[]{
                Attribute.LineNumberTable,
                Attribute.LocalVariableTable,
                Attribute.LocalVariableTypeTable
        };
        for (Method method : classFile.methods) {
            String methodName = method.getName(classFile.constant_pool);
            Code_attribute code = (Code_attribute) method.attributes.get(Attribute.Code);
            for (String attr : debugAttributes) {
                if (code.attributes.get(attr) != null) {
                    throw new AssertionError("Debug attribute was not removed: " + attr +
                            " from method " + classFile.getName() + "#" + methodName);
                }
            }
        }
    }
}
