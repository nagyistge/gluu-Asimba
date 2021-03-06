package org.asimba.util.saml2.storage.artifact.jgroups;

import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.InputStream;
import java.util.Properties;
import java.util.Scanner;

import org.apache.commons.lang3.SerializationUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.asimba.engine.cluster.JGroupsCluster;
import org.jgroups.JChannel;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.opensaml.DefaultBootstrap;
import org.opensaml.common.binding.artifact.SAMLArtifactMap.SAMLArtifactMapEntry;
import org.opensaml.saml1.core.NameIdentifier;
import org.opensaml.saml1.core.impl.NameIdentifierBuilder;
import org.opensaml.xml.ConfigurationException;
import org.w3c.dom.Element;

import com.alfaariss.oa.api.configuration.IConfigurationManager;
import com.alfaariss.oa.engine.core.configuration.ConfigurationManager;
import com.alfaariss.oa.util.saml2.storage.artifact.ArtifactMapEntry;
import static org.hamcrest.core.IsEqual.equalTo;
import org.jgroups.blocks.ReplicatedHashMap;
import static org.junit.Assert.assertThat;

public class JGroupsArtifactMapFactoryTest {

	private static final Log _oLogger = LogFactory.getLog(JGroupsArtifactMapFactoryTest.class);
	
	private static final String FILENAME_CONFIG = "jgroupsfactory-config-ok.xml";
	private static final String FILENAME_CONFIG_NONBLOCKING = "jgroupsfactory-config-nonblocking.xml";

	private static final long EXPIRATION_FOR_TEST = 500000;

	String[] AvailableNodeNames = {"one", "two", "three", "four", "five"};
	JGroupsArtifactMapFactory[] Factories = new JGroupsArtifactMapFactory[AvailableNodeNames.length];

	
	@Before
	public void before() throws ConfigurationException {
		DefaultBootstrap.bootstrap();
	}
	
	
	@After
	public void after() throws Exception {
		for (int i = 0; i < AvailableNodeNames.length; ++i) {
			if (Factories[i] != null) {
				Factories[i].stop();
				Factories[i] = null;
			}
		}		
	}
		

	@Test
	public void test01_ArtifactMapEntrySerializable() throws Exception {
		ArtifactMapEntry ae = new ArtifactMapEntry();
		
		try {
			SerializationUtils.serialize(ae);
		}
		catch (Exception e) {
			_oLogger.error("Object of class ArtifactMapEntry cannot be serialized", e);
			throw e;
		}
	}


    @Test
    public void test01a_ArtifactMapDefaultConfiguration() throws Exception {
        JGroupsArtifactMapFactory artifactFactory = createJGroupsArtifactMapFactory(0,1000, FILENAME_CONFIG);
        assertThat(artifactFactory.isBlockingUpdates(), equalTo(true));
        // confirm that the timeout is equal to the default timeout of a ReplicatedHashMap
        assertThat(artifactFactory.getTimeout(), equalTo((new ReplicatedHashMap<String, String>(new JChannel()).getTimeout())));
    }
    
    
    @Test
    public void test01b_ArtifactMapNonBlockingConfiguration() throws Exception {
        JGroupsArtifactMapFactory artifactFactory = createJGroupsArtifactMapFactory(0,1000, FILENAME_CONFIG_NONBLOCKING);
        assertThat(artifactFactory.isBlockingUpdates(), equalTo(false));
        // confirm that the timeout is equal to the default timeout of a ReplicatedHashMap
        assertThat(artifactFactory.getTimeout(), equalTo(100l));
    }
    
    
	@Test
	public void test02_OneNodeOneEntry() throws Exception {
		testNArtifactMapFactories(1, 1);
	}
	
	
	@Test
	public void test03_TwoNodesManyEntries() throws Exception {
		testNArtifactMapFactories(2, 100);
	}
	
	
	@Test
	public void test04_RunTwoNodesAndAddOne() throws Exception {
		final int nEntries = 100;
		final int expectEntries = nEntries * 2;
		testNArtifactMapFactories(2, nEntries);
		assertThat(Factories[0].size(), equalTo(expectEntries));
		assertThat(Factories[1].size(), equalTo(expectEntries));
		assertThat(Factories[2], equalTo(null));
		createJGroupsArtifactMapFactory(2, EXPIRATION_FOR_TEST, FILENAME_CONFIG);
		JGroupsArtifactMapFactory addedFactory = Factories[2];
		assertThat(addedFactory, not(equalTo(null)));
		assertThat(addedFactory.size(), equalTo(expectEntries));
		String id = "justSomeIDString";
		addedFactory.put(id, id, id, new NameIdentifierBuilder().buildObject());
		assertThat(addedFactory.size(), equalTo(expectEntries + 1));
		assertThat(Factories[0].size(), equalTo(expectEntries + 1));
		assertThat(Factories[1].size(), equalTo(expectEntries + 1));
	}
	
	
    /**
     * Test removal of expired Artifacts
     * @throws java.lang.Exception
     */
    @Test
    public void test05_RemoveExpiredArtifacts() throws Exception {
    	final String SOME_ID = "SomeId";
        JGroupsArtifactMapFactory artifactFactory = createJGroupsArtifactMapFactory(0,1000, FILENAME_CONFIG);
        NameIdentifier entry = new NameIdentifierBuilder().buildObject();

        artifactFactory.put(SOME_ID, SOME_ID, SOME_ID, entry);
        assertTrue(artifactFactory.contains(SOME_ID));

		artifactFactory.removeExpired();

        Thread.sleep(1000);

        artifactFactory.removeExpired();
        assertFalse(artifactFactory.contains(SOME_ID));
    }
	

	
	
	private void testNArtifactMapFactories(int nNodes, int nEntries) throws Exception {
		int firstFreeNode = getFirstUnusedNode();
		if (firstFreeNode + nNodes > AvailableNodeNames.length) {
			_oLogger.error("Not enough unused nodes left");
			throw new Exception("Not enough unused nodes left");
		}
		
		for (int i = firstFreeNode; i < nNodes; ++i) {
			createJGroupsArtifactMapFactory(i, EXPIRATION_FOR_TEST, FILENAME_CONFIG);
		}

		int persisted = 0;
		for (int i = 0; i < nNodes; ++i) {
			for (int j = 0; j < nEntries; ++j) {
				String id = (new Integer(i)).toString() + "." + (new Integer(j)).toString();
				Factories[i].put(id, id, id, new NameIdentifierBuilder().buildObject());
				for (int k = 0; k < nNodes; ++k) {
					SAMLArtifactMapEntry rSAMLObject = Factories[i].get(id);
					assertThat("Assertion failed at: " + k, rSAMLObject, not(equalTo(null)));
					assertThat(rSAMLObject.getArtifact(), equalTo(id));
				}
				++persisted;
			}
		}
		for (int i = 0; i < nNodes; ++i) {
			assertThat(Factories[i].size(), equalTo(persisted));
		}		
	}
	
	
	private int getFirstUnusedNode() throws Exception {
		for (int i = 0; i < AvailableNodeNames.length; ++i) {
			if (Factories[i] == null) {
				return i;
			}
		}
		
		String message = "No unused nodes left";
		_oLogger.error(message);
		throw new Exception(message);
	}
	

	private JGroupsArtifactMapFactory createJGroupsArtifactMapFactory(int n, long expiration, String configFile) throws Exception
	{
		String id = AvailableNodeNames[n];
		System.setProperty(JGroupsCluster.PROP_ASIMBA_NODE_ID, id);

		IConfigurationManager oConfigManager = readConfigElementFromResource(configFile);

		Element eClusterConfig = oConfigManager.getSection(
				null, "cluster", "id=test");            
		assertThat(eClusterConfig, not(equalTo(null)));

		JGroupsCluster oCluster = new JGroupsCluster();
		oCluster.start(oConfigManager, eClusterConfig);
		JChannel jChannel = (JChannel) oCluster.getChannel();
		assertThat(jChannel, not(equalTo(null)));
		_oLogger.info("JCluster address:" + jChannel.getAddressAsString());

		JGroupsArtifactMapFactory oSessionFactory = Factories[n] = new JGroupsArtifactMapFactory();
		oSessionFactory.startForTesting(oConfigManager, eClusterConfig,  oCluster,
				( expiration == 0 ) ? EXPIRATION_FOR_TEST : expiration);

		return oSessionFactory;
	}
	
	
	private IConfigurationManager readConfigElementFromResource(String filename) throws Exception
	{
		
		InputStream oIS = JGroupsArtifactMapFactoryTest.class.getClassLoader().getResourceAsStream(filename);
		String sConfig;
		
		try (Scanner s = new Scanner(oIS, "UTF-8")) {
			s.useDelimiter("\\A");
			sConfig = s.next();
		}
		
		//_oLogger.info("XML Read: [" + sConfig + "]");
		
		Properties oProperties = new Properties();
		oProperties.put("configuration.handler.class", 
				"com.alfaariss.oa.util.configuration.handler.text.PlainTextConfigurationHandler");
		oProperties.put("config", sConfig);
		
		ConfigurationManager oConfigManager = ConfigurationManager.getInstance();
		oConfigManager.start(oProperties);
		
		return oConfigManager;
	}
}
