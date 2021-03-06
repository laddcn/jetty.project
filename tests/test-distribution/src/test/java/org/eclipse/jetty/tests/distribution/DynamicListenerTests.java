//
// ========================================================================
// Copyright (c) 1995-2020 Mort Bay Consulting Pty Ltd and others.
//
// This program and the accompanying materials are made available under
// the terms of the Eclipse Public License 2.0 which is available at
// https://www.eclipse.org/legal/epl-2.0
//
// This Source Code may also be made available under the following
// Secondary Licenses when the conditions for such availability set
// forth in the Eclipse Public License, v. 2.0 are satisfied:
// the Apache License v2.0 which is available at
// https://www.apache.org/licenses/LICENSE-2.0
//
// SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
// ========================================================================
//

package org.eclipse.jetty.tests.distribution;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.util.IO;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DynamicListenerTests
    extends AbstractDistributionTest
{
    @Test
    public void testSimpleWebAppWithJSP() throws Exception
    {
        Path jettyBase = Files.createTempDirectory("jetty_base");
        String jettyVersion = System.getProperty("jettyVersion");
        DistributionTester distribution = DistributionTester.Builder.newInstance()
            .jettyBase(jettyBase)
            .jettyVersion(jettyVersion)
            .mavenLocalRepository(System.getProperty("mavenRepoPath"))
            .build();

        String[] args1 = {
            "--create-startd",
            "--approve-all-licenses",
            "--add-to-start=resources,server,http,webapp,deploy,jsp,jmx,servlet,servlets,security,websocket"
        };
        try (DistributionTester.Run run1 = distribution.start(args1))
        {
            assertTrue(run1.awaitFor(5, TimeUnit.SECONDS));
            assertEquals(0, run1.getExitValue());

            File war = distribution.resolveArtifact("org.eclipse.jetty:test-jetty-webapp:war:" + jettyVersion);
            distribution.installWarFile(war, "test");

            Path etc = Paths.get(jettyBase.toString(),"etc");
            if (!Files.exists(etc))
            {
                Files.createDirectory(etc);
            }

            Files.copy(Paths.get("src/test/resources/realm.ini"),
                       Paths.get(jettyBase.toString(),"start.d").resolve("realm.ini"));
            Files.copy(Paths.get("src/test/resources/realm.properties"),
                       etc.resolve("realm.properties"));
            Files.copy(Paths.get("src/test/resources/test-realm.xml"),
                       etc.resolve("test-realm.xml"));

            int port = distribution.freePort();
            try (DistributionTester.Run run2 = distribution.start("jetty.http.port=" + port))
            {
                assertTrue(run2.awaitConsoleLogsFor("Started Server@", 10, TimeUnit.SECONDS));

                startHttpClient();
                ContentResponse response = client.GET("http://localhost:" + port + "/test/testservlet/foo");
                assertEquals(HttpStatus.OK_200, response.getStatus());
                String content = response.getContentAsString();
                System.out.println(content);
                assertThat(content, containsString("All Good"));
                assertThat(content, containsString("requestInitialized"));
                assertThat(content, containsString("requestInitialized"));
                assertThat(content, not(containsString("<%")));
            }
        }
        finally
        {
            IO.delete(jettyBase.toFile());
        }
    }

}
