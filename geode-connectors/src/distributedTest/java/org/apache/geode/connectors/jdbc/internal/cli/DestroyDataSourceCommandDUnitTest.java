/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.geode.connectors.jdbc.internal.cli;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import org.assertj.core.api.AssertionsForClassTypes;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import org.apache.geode.distributed.internal.InternalConfigurationPersistenceService;
import org.apache.geode.distributed.internal.InternalLocator;
import org.apache.geode.internal.jndi.JNDIInvoker;
import org.apache.geode.management.internal.configuration.domain.Configuration;
import org.apache.geode.management.internal.configuration.utils.XmlUtils;
import org.apache.geode.test.dunit.rules.ClusterStartupRule;
import org.apache.geode.test.dunit.rules.MemberVM;
import org.apache.geode.test.junit.rules.GfshCommandRule;
import org.apache.geode.test.junit.rules.VMProvider;

public class DestroyDataSourceCommandDUnitTest {
  private MemberVM locator, server1, server2;

  @Rule
  public ClusterStartupRule cluster = new ClusterStartupRule();

  @Rule
  public GfshCommandRule gfsh = new GfshCommandRule();

  @Before
  public void before() throws Exception {
    locator = cluster.startLocatorVM(0);
    server1 = cluster.startServerVM(1, locator.getPort());
    server2 = cluster.startServerVM(2, locator.getPort());

    gfsh.connectAndVerify(locator);

    gfsh.execute(
        "create data-source --name=datasource1 --url=\"jdbc:derby:newDB;create=true\"");
  }

  @Test
  public void testDestroyDataSource() {
    // assert that there is a datasource
    VMProvider
        .invokeInEveryMember(
            () -> assertThat(JNDIInvoker.getBindingNamesRecursively(JNDIInvoker.getJNDIContext()))
                .containsKey("java:datasource1").containsValue(
                    "org.apache.geode.internal.datasource.GemFireBasicDataSource"),
            server1, server2);

    gfsh.executeAndAssertThat("destroy data-source --name=datasource1").statusIsSuccess()
        .tableHasColumnOnlyWithValues("Member", "server-1", "server-2")
        .tableHasColumnOnlyWithValues("Message",
            "Data source \"datasource1\" destroyed on \"server-1\"",
            "Data source \"datasource1\" destroyed on \"server-2\"");

    // verify cluster config is updated
    locator.invoke(() -> {
      InternalLocator internalLocator = ClusterStartupRule.getLocator();
      AssertionsForClassTypes.assertThat(internalLocator).isNotNull();
      InternalConfigurationPersistenceService ccService =
          internalLocator.getConfigurationPersistenceService();
      Configuration configuration = ccService.getConfiguration("cluster");
      Document document = XmlUtils.createDocumentFromXml(configuration.getCacheXmlContent());
      NodeList jndiBindings = document.getElementsByTagName("jndi-binding");

      AssertionsForClassTypes.assertThat(jndiBindings.getLength()).isEqualTo(0);

      boolean found = false;
      for (int i = 0; i < jndiBindings.getLength(); i++) {
        Element eachBinding = (Element) jndiBindings.item(i);
        if (eachBinding.getAttribute("jndi-name").equals("datasource1")) {
          found = true;
          break;
        }
      }
      AssertionsForClassTypes.assertThat(found).isFalse();
    });

    // verify datasource does not exists
    VMProvider.invokeInEveryMember(
        () -> AssertionsForClassTypes.assertThat(JNDIInvoker.getNoOfAvailableDataSources())
            .isEqualTo(0),
        server1, server2);

    // bounce server1 and assert that there is still no datasource received from cluster config
    server1.stop(false);
    server1 = cluster.startServerVM(1, locator.getPort());

    // verify no datasource from cluster config
    server1.invoke(() -> {
      AssertionsForClassTypes.assertThat(JNDIInvoker.getNoOfAvailableDataSources()).isEqualTo(0);
    });
  }

  @Test
  public void destroyDataSourceFailsIfInUseByJdbcMapping() {
    gfsh.executeAndAssertThat("create region --name=myRegion --type=REPLICATE").statusIsSuccess();
    gfsh.executeAndAssertThat(
        "create jdbc-mapping --data-source=datasource1 --pdx-name=myPdxClass --region=myRegion")
        .statusIsSuccess();

    gfsh.executeAndAssertThat("destroy data-source --name=datasource1").statusIsError()
        .containsOutput(
            "Data source named \"datasource1\" is still being used by region \"myRegion\"."
                + " Use destroy jdbc-mapping --region=myRegion and then try again.");
  }
}
