/*
 * Copyright 2008 Sun Microsystems, Inc. All rights reserved.
 * 
 * U.S. Government Rights - Commercial software. Government users 
 * are subject to the Sun Microsystems, Inc. standard license agreement
 * and applicable provisions of the FAR and its supplements.
 * 
 * Use is subject to license terms.
 * 
 * This distribution may include materials developed by third parties.
 * Sun, Sun Microsystems, the Sun logo, Java and Project Identity 
 * Connectors are trademarks or registered trademarks of Sun 
 * Microsystems, Inc. or its subsidiaries in the U.S. and other
 * countries.
 * 
 * UNIX is a registered trademark in the U.S. and other countries,
 * exclusively licensed through X/Open Company, Ltd. 
 * 
 * -----------
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2008 Sun Microsystems, Inc. All rights reserved. 
 * 
 * The contents of this file are subject to the terms of the Common Development
 * and Distribution License(CDDL) (the License).  You may not use this file
 * except in  compliance with the License. 
 * 
 * You can obtain a copy of the License at
 * http://identityconnectors.dev.java.net/CDDLv1.0.html
 * See the License for the specific language governing permissions and 
 * limitations under the License.  
 * 
 * When distributing the Covered Code, include this CDDL Header Notice in each
 * file and include the License file at identityconnectors/legal/license.txt.
 * If applicable, add the following below this CDDL Header, with the fields 
 * enclosed by brackets [] replaced by your own identifying information: 
 * "Portions Copyrighted [year] [name of copyright owner]"
 * -----------
 */
package org.identityconnectors.vms;

import static org.identityconnectors.vms.VmsConstants.ATTR_ALGORITHM;
import static org.identityconnectors.vms.VmsConstants.ATTR_ASTLM;
import static org.identityconnectors.vms.VmsConstants.ATTR_BIOLM;
import static org.identityconnectors.vms.VmsConstants.ATTR_BYTLM;
import static org.identityconnectors.vms.VmsConstants.ATTR_DEFPRIVILEGES;
import static org.identityconnectors.vms.VmsConstants.ATTR_DIOLM;
import static org.identityconnectors.vms.VmsConstants.ATTR_EXPIRATION;
import static org.identityconnectors.vms.VmsConstants.ATTR_FILLM;
import static org.identityconnectors.vms.VmsConstants.ATTR_FLAGS;
import static org.identityconnectors.vms.VmsConstants.ATTR_JTQUOTA;
import static org.identityconnectors.vms.VmsConstants.ATTR_MAXACCTJOBS;
import static org.identityconnectors.vms.VmsConstants.ATTR_MAXDETACH;
import static org.identityconnectors.vms.VmsConstants.ATTR_OWNER;
import static org.identityconnectors.vms.VmsConstants.ATTR_PBYTLM;
import static org.identityconnectors.vms.VmsConstants.ATTR_PRCLM;
import static org.identityconnectors.vms.VmsConstants.ATTR_PRIMEDAYS;
import static org.identityconnectors.vms.VmsConstants.ATTR_PRIORITY;
import static org.identityconnectors.vms.VmsConstants.ATTR_PRIVILEGES;
import static org.identityconnectors.vms.VmsConstants.ATTR_PWDEXPIRED;
import static org.identityconnectors.vms.VmsConstants.ATTR_QUEPRIO;
import static org.identityconnectors.vms.VmsConstants.ATTR_SHRFILLM;
import static org.identityconnectors.vms.VmsConstants.ATTR_TQELM;
import static org.identityconnectors.vms.VmsConstants.ATTR_WSDEFAULT;
import static org.identityconnectors.vms.VmsConstants.ATTR_WSEXTENT;
import static org.identityconnectors.vms.VmsConstants.ATTR_WSQUOTA;
import static org.identityconnectors.vms.VmsConstants.DAYS_FRI;
import static org.identityconnectors.vms.VmsConstants.DAYS_MON;
import static org.identityconnectors.vms.VmsConstants.DAYS_NOT;
import static org.identityconnectors.vms.VmsConstants.DAYS_SAT;
import static org.identityconnectors.vms.VmsConstants.DAYS_SUN;
import static org.identityconnectors.vms.VmsConstants.DAYS_THU;
import static org.identityconnectors.vms.VmsConstants.DAYS_TUE;
import static org.identityconnectors.vms.VmsConstants.DAYS_WED;
import static org.identityconnectors.vms.VmsConstants.FLAG_DISUSER;
import static org.identityconnectors.vms.VmsConstants.FLAG_NOT;
import static org.identityconnectors.vms.VmsConstants.FLAG_PWD_EXPIRED;
import static org.identityconnectors.vms.VmsConstants.PRIV_NETMBX;
import static org.identityconnectors.vms.VmsConstants.PRIV_TMPMBX;

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import junit.framework.Assert;

import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.exceptions.AlreadyExistsException;
import org.identityconnectors.framework.common.exceptions.UnknownUidException;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.AttributeUtil;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.ConnectorObjectBuilder;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.ObjectClassInfo;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.OperationalAttributes;
import org.identityconnectors.framework.common.objects.ResultsHandler;
import org.identityconnectors.framework.common.objects.Schema;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.common.objects.filter.AndFilter;
import org.identityconnectors.framework.common.objects.filter.ContainsFilter;
import org.identityconnectors.framework.common.objects.filter.EndsWithFilter;
import org.identityconnectors.framework.common.objects.filter.EqualsFilter;
import org.identityconnectors.framework.common.objects.filter.Filter;
import org.identityconnectors.framework.common.objects.filter.NotFilter;
import org.identityconnectors.framework.common.objects.filter.StartsWithFilter;
import org.identityconnectors.framework.test.TestHelpers;
import org.junit.BeforeClass;
import org.junit.Test;


public class VmsConnectorTests {

    // Connector Configuration information
    //
    private static final String LINE_TERMINATOR     = "\r\n";
    private static final String SHELL_PROMPT        = "$";
    private static String TEST_USER_START           = "TEST";
    private static String TEST_USER_MIDDLE          = "ST";
    private static String HOST_NAME;
    private static String SYSTEM_PASSWORD;
    private static String SYSTEM_USER;


    public static void main(String[] args) {
        VmsConnectorTests tests = new VmsConnectorTests();
        try {
            tests.testCreate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @BeforeClass
    public static void before() {
        HOST_NAME         = TestHelpers.getProperty("HOST_NAME", null);
        SYSTEM_PASSWORD   = TestHelpers.getProperty("SYSTEM_PASSWORD", null);
        SYSTEM_USER       = TestHelpers.getProperty("SYSTEM_USER", null);
        Assert.assertNotNull("HOST_NAME must be specified", HOST_NAME);
        Assert.assertNotNull("SYSTEM_PASSWORD must be specified", SYSTEM_PASSWORD);
        Assert.assertNotNull("SYSTEM_USER must be specified", SYSTEM_USER);
    }

    @Test
    public void testBadPort() throws Exception {
        {
            VmsConfiguration config = createConfiguration();
            config.setHostPortNumber(100000);
            try {
                config.validate();
                Assert.fail("no exception for bad port");
            } catch (Exception e) {
                // expected
            }
        }
    }

    @Test
    public void testNullsInConfig() throws Exception {
        {
            VmsConfiguration config = new VmsConfiguration();
            config.getHostLineTerminator();
            // Validate that setting this to null doesn't error out
            //
            config.setHostLineTerminator(null);
            config.getHostLineTerminator();
        }
        
        // Ensure that validation catches nul fields
        //
        try {
            VmsConfiguration config = createConfiguration();
            config.setHostLineTerminator(null);
            config.validate();
            Assert.fail("expected exception");
        } catch (RuntimeException rte) {
            // expected
        }
        try {
            VmsConfiguration config = createConfiguration();
            config.setSSH(null);
            config.validate();
            Assert.fail("expected exception");
        } catch (RuntimeException rte) {
            // expected
        }
        try {
            VmsConfiguration config = createConfiguration();
            config.setHostShellPrompt(null);
            config.validate();
            Assert.fail("expected exception");
        } catch (RuntimeException rte) {
            // expected
        }
        try {
            VmsConfiguration config = createConfiguration();
            config.setConnectScript(null);
            config.validate();
            Assert.fail("expected exception");
        } catch (RuntimeException rte) {
            // expected
        }
        try {
            VmsConfiguration config = createConfiguration();
            config.setHostNameOrIpAddr(null);
            config.validate();
            Assert.fail("expected exception");
        } catch (RuntimeException rte) {
            // expected
        }
        try {
            VmsConfiguration config = createConfiguration();
            config.setHostPortNumber(null);
            config.validate();
            Assert.fail("expected exception");
        } catch (RuntimeException rte) {
            // expected
        }
        try {
            VmsConfiguration config = createConfiguration();
            config.setUserName(null);
            config.validate();
            Assert.fail("expected exception");
        } catch (RuntimeException rte) {
            // expected
        }
        try {
            VmsConfiguration config = createConfiguration();
            config.setPassword(null);
            config.validate();
            Assert.fail("expected exception");
        } catch (RuntimeException rte) {
            // expected
        }
    }

    @Test
    public void testListAllUsers() throws Exception {
        VmsConfiguration config = createConfiguration();
        VmsConnector info = createConnector(config);
        try {
            TestHandler handler = new TestHandler();
            TestHelpers.search(info,ObjectClass.ACCOUNT, null, handler, null);
            for (ConnectorObject user : handler) {
                System.out.println("Read User:"+user.getUid().getValue());
            }
        } finally {
            info.dispose();
        }
    }

    @Test
    public void testTest() throws Exception {
        VmsConfiguration config = createConfiguration();
        VmsConnector info = createConnector(config);
        try {
            info.checkAlive();
        } finally {
            info.dispose();
        }
    }

    @Test
    public void testGetSpecifiedUser() throws Exception {
        VmsConfiguration config = createConfiguration();
        VmsConnector info = createConnector(config);
        try {
            Set<Attribute> attrs = fillInSampleUser(getTestUser());
    
            // Create the account if it doesn't already exist
            //
            try {
                info.create(ObjectClass.ACCOUNT, attrs, null);
            } catch (AlreadyExistsException rte) {
                // Ignore
            }
            // Filter on Name
            //
            {
                TestHandler handler = new TestHandler();
                boolean found = false;
                int count = 0;
                TestHelpers.search(info,ObjectClass.ACCOUNT, new EqualsFilter(AttributeBuilder.build(Name.NAME, getTestUser())), handler, null);
                for (ConnectorObject user : handler) {
                    if (getTestUser().equals(user.getUid().getUidValue())) {
                        found = true;
                        System.out.println(user);
                    }
                    count++;
                }
                Assert.assertTrue(count==1);
                Assert.assertTrue(found);
            }
            // Filter on Uid
            //
            {
                TestHandler handler = new TestHandler();
                boolean found = false;
                int count = 0;
                TestHelpers.search(info,ObjectClass.ACCOUNT, new EqualsFilter(AttributeBuilder.build(Uid.NAME, getTestUser())), handler, null);
                for (ConnectorObject user : handler) {
                    if (getTestUser().equals(user.getUid().getUidValue())) {
                        found = true;
                        System.out.println(user);
                    }
                    count++;
                }
                Assert.assertTrue(count==1);
                Assert.assertTrue(found);
            }
            
            // retry, but specify only 2 attributes
            //
            {
                TestHandler handler = new TestHandler();
                boolean found = false;
                int count = 0;
                String[] attributesToGet = { "BYTLM", "PRIVILEGES" };
                Map<String, Object> optionsMap = new HashMap<String, Object>();
                optionsMap.put(OperationOptions.OP_ATTRIBUTES_TO_GET, attributesToGet); 
                OperationOptions options = new OperationOptions(optionsMap);
                TestHelpers.search(info,ObjectClass.ACCOUNT, new EqualsFilter(AttributeBuilder.build(Name.NAME, getTestUser())), handler, options);
                for (ConnectorObject user : handler) {
                    if (getTestUser().equals(user.getUid().getUidValue())) {
                        found = true;
                        // Name, Uid forced in
                        //
                        Assert.assertEquals(4, user.getAttributes().size());
                        System.out.println(user);
                    }
                    count++;
                }
                Assert.assertTrue(count==1);
                Assert.assertTrue(found);
            }
        } finally {
            info.dispose();
        }

    }
    
    @Test
    public void testFilters() throws Exception {
        VmsConfiguration config = createConfiguration();
        VmsConnector info = createConnector(config);
        try {
            Set<Attribute> attrs = fillInSampleUser(getTestUser());
    
            // Create the account if it doesn't already exist
            //
            try {
                info.create(ObjectClass.ACCOUNT, attrs, null);
            } catch (AlreadyExistsException rte) {
                // Ignore
            }
            Filter filter= null;
            
            filter = new EqualsFilter(AttributeBuilder.build(Name.NAME, getTestUser()));
            getUserViaFilter(info, filter, true, true, getTestUser());
            filter = new EndsWithFilter(AttributeBuilder.build(Name.NAME, getTestUserEnd()));
            getUserViaFilter(info, filter, false, true, ".*"+getTestUserEnd());
            filter = new StartsWithFilter(AttributeBuilder.build(Name.NAME, TEST_USER_START));
            getUserViaFilter(info, filter, false, true, TEST_USER_START+".*");
            filter = new ContainsFilter(AttributeBuilder.build(Name.NAME, TEST_USER_MIDDLE));
            getUserViaFilter(info, filter, false, true, ".*"+TEST_USER_MIDDLE+".*");
            filter = new AndFilter(
                    new StartsWithFilter(AttributeBuilder.build(Name.NAME, TEST_USER_START)),
                    new ContainsFilter(AttributeBuilder.build(Name.NAME, TEST_USER_MIDDLE))
                    );
            getUserViaFilter(info, filter, false, true, TEST_USER_START+".*");
            // Since we should be doing no filtering, will always be found
            //
            filter = new NotFilter(new EqualsFilter(AttributeBuilder.build(Name.NAME, getTestUser())));
            getUserViaFilter(info, filter, false, true, null);
            filter = new NotFilter(new EndsWithFilter(AttributeBuilder.build(Name.NAME, getTestUserEnd())));
            getUserViaFilter(info, filter, false, true, null);
            filter = new NotFilter(new StartsWithFilter(AttributeBuilder.build(Name.NAME, TEST_USER_START)));
            getUserViaFilter(info, filter, false, true, null);
            filter = new NotFilter(new ContainsFilter(AttributeBuilder.build(Name.NAME, TEST_USER_MIDDLE)));
            getUserViaFilter(info, filter, false, true, null);
        } finally {
            info.dispose();
        }

    }

    private void getUserViaFilter(VmsConnector connector, Filter filter, boolean exactlyOne, boolean shouldFind, String patternString) {
        TestHandler handler = new TestHandler();
        TestHelpers.search(connector,ObjectClass.ACCOUNT, filter, handler, null);
        boolean found = false;
        int count = 0;
        for (ConnectorObject user : handler) {
            String userId = user.getUid().getUidValue();
            if (patternString!=null) {
                if (!Pattern.matches(patternString, userId)) {
                    System.out.println(userId);
                }
                Assert.assertTrue(Pattern.matches(patternString, userId));
            }
            if (getTestUser().equals(userId)) {
                found = true;
                System.out.println(user);
            }
            count++;
        }
        Assert.assertTrue(!exactlyOne || count==1);
        Assert.assertTrue(shouldFind==found);
    }

    @Test
    public void testGetNonexistentUser() throws Exception {
        VmsConfiguration config = createConfiguration();
        VmsConnector info = createConnector(config);

        try {
            deleteUser(getTestUser(), info);
            TestHandler handler = new TestHandler();
            TestHelpers.search(info,ObjectClass.ACCOUNT, new EqualsFilter(AttributeBuilder.build(Name.NAME, getTestUser())), handler, null);
            boolean found = false;
            int count = 0;
            for (ConnectorObject user : handler) {
                if (getTestUser().equals(user.getUid().getUidValue())) {
                    found = true;
                    System.out.println(user);
                }
                count++;
            }
            Assert.assertTrue(count==0);
            Assert.assertTrue(!found);
        } finally {
            info.dispose();
        }
    }

    @Test
    public void testModifyUser() throws Exception {
        VmsConfiguration config = createConfiguration();
        VmsConnector info = createConnector(config);

        try {
            Set<Attribute> attrs = fillInSampleUser(getTestUser());
    
            // Recreate the account (so that PRIMEDAYS is as expected)
            //
            deleteUser(getTestUser(), info);
            info.create(ObjectClass.ACCOUNT, attrs, null);
    
            ConnectorObject user = getUser(getTestUser());
            Attribute primedays = user.getAttributeByName(ATTR_PRIMEDAYS);
            List<Object> oldList = primedays.getValue();
            Assert.assertFalse(oldList.contains(DAYS_WED));
            List<Object> newList = new LinkedList<Object>();
            newList.addAll(oldList);
            newList.add(DAYS_WED);
    
            ConnectorObjectBuilder builder = new ConnectorObjectBuilder();
            builder.setUid(getTestUser());
            builder.setName(getTestUser());
            builder.addAttribute(ATTR_PRIMEDAYS, newList);
            ConnectorObject newUser = builder.build();
            info.update(newUser.getObjectClass(), newUser.getAttributes(), null);
    
            user = getUser(getTestUser());
            primedays = user.getAttributeByName(ATTR_PRIMEDAYS);
            oldList = primedays.getValue();
            Assert.assertTrue(oldList.contains(DAYS_WED));
        } finally {
            info.dispose();
        }
    }
    
    @Test
    public void testModifyAttributes() throws Exception {
        VmsConfiguration config = createConfiguration();
        VmsConnector info = createConnector(config);

        try {
            Set<Attribute> attrs = fillInSampleUser(getTestUser());
    
            // Recreate the account
            //
            deleteUser(getTestUser(), info);
            info.create(ObjectClass.ACCOUNT, attrs, null);
    
            // Test the write-only attributes
            //
            testModifyUserAttribute(info, AttributeBuilder.build(ATTR_ALGORITHM, "BOTH=VMS"), false);
            testModifyUserAttribute(info, AttributeBuilder.build(ATTR_ALGORITHM, "CURRENT=CUSTOMER=200"), false);
            
            testModifyUserAttribute(info, AttributeBuilder.build(ATTR_OWNER, "SUPERMAN"));
            // Conversionns are inexact : testModifyUserAttribute(info.connector, AttributeBuilder.build(OperationalAttributes.PASSWORD_EXPIRATION_DATE_NAME, new Date().getTime()));
            testModifyUserAttribute(info, AttributeBuilder.build(ATTR_FILLM, Integer.valueOf(12)));
            testModifyUserAttribute(info, AttributeBuilder.build(ATTR_BYTLM, Integer.valueOf(12)));
            testModifyUserAttribute(info, AttributeBuilder.build(ATTR_MAXACCTJOBS, Integer.valueOf(12)));
            testModifyUserAttribute(info, AttributeBuilder.build(ATTR_SHRFILLM, Integer.valueOf(12)));
            testModifyUserAttribute(info, AttributeBuilder.build(ATTR_PBYTLM, Integer.valueOf(2)));
            testModifyUserAttribute(info, AttributeBuilder.build(ATTR_MAXDETACH, "12"));
            testModifyUserAttribute(info, AttributeBuilder.build(ATTR_BIOLM, Integer.valueOf(12)));
            testModifyUserAttribute(info, AttributeBuilder.build(ATTR_JTQUOTA, Integer.valueOf(12)));
            testModifyUserAttribute(info, AttributeBuilder.build(ATTR_PRCLM, Integer.valueOf(12)));
            testModifyUserAttribute(info, AttributeBuilder.build(ATTR_DIOLM, Integer.valueOf(12)));
            testModifyUserAttribute(info, AttributeBuilder.build(ATTR_WSDEFAULT, Integer.valueOf(12)));
            testModifyUserAttribute(info, AttributeBuilder.build(ATTR_PRIORITY, Integer.valueOf(12)));
            testModifyUserAttribute(info, AttributeBuilder.build(ATTR_ASTLM, Integer.valueOf(12)));
            testModifyUserAttribute(info, AttributeBuilder.build(ATTR_WSQUOTA, Integer.valueOf(12)));
            testModifyUserAttribute(info, AttributeBuilder.build(ATTR_QUEPRIO, Integer.valueOf(12)));
            testModifyUserAttribute(info, AttributeBuilder.build(ATTR_TQELM, Integer.valueOf(12)));
            testModifyUserAttribute(info, AttributeBuilder.build(ATTR_WSEXTENT, Integer.valueOf(12)));
        } finally {
            info.dispose();
        }
    }

    @Test
    public void testEnableDisable() throws Exception {
        VmsConfiguration config = createConfiguration();
        VmsConnector info = createConnector(config);

        try {
            Set<Attribute> attrs = fillInSampleUser(getTestUser());
    
            // Recreate the account
            //
            deleteUser(getTestUser(), info);
            info.create(ObjectClass.ACCOUNT, attrs, null);

            // Show that we can enable
            //
            {
                ConnectorObjectBuilder builder = new ConnectorObjectBuilder();
                builder.setUid(getTestUser());
                builder.setName(getTestUser());
                Attribute enable = AttributeBuilder.build(OperationalAttributes.ENABLE_NAME, Boolean.TRUE);
                builder.addAttribute(enable);
                ConnectorObject newUser = builder.build();
                info.update(newUser.getObjectClass(), newUser.getAttributes(), null);
    
                ConnectorObject user = getUser(getTestUser());
                enable = user.getAttributeByName(OperationalAttributes.ENABLE_NAME);
                Assert.assertTrue(AttributeUtil.getBooleanValue(enable));
            }
            // Show that we can disable
            //
            {
                ConnectorObjectBuilder builder = new ConnectorObjectBuilder();
                builder.setUid(getTestUser());
                builder.setName(getTestUser());
                Attribute enable = AttributeBuilder.build(OperationalAttributes.ENABLE_NAME, Boolean.FALSE);
                builder.addAttribute(enable);
                ConnectorObject newUser = builder.build();
                info.update(newUser.getObjectClass(), newUser.getAttributes(), null);
    
                ConnectorObject user = getUser(getTestUser());
                enable = user.getAttributeByName(OperationalAttributes.ENABLE_NAME);
                Assert.assertFalse(AttributeUtil.getBooleanValue(enable));
            }
            // Show that we can reenable
            //
            {
                ConnectorObjectBuilder builder = new ConnectorObjectBuilder();
                builder.setUid(getTestUser());
                builder.setName(getTestUser());
                Attribute enable = AttributeBuilder.build(OperationalAttributes.ENABLE_NAME, Boolean.TRUE);
                builder.addAttribute(enable);
                ConnectorObject newUser = builder.build();
                info.update(newUser.getObjectClass(), newUser.getAttributes(), null);
    
                ConnectorObject user = getUser(getTestUser());
                enable = user.getAttributeByName(OperationalAttributes.ENABLE_NAME);
                Assert.assertTrue(AttributeUtil.getBooleanValue(enable));
            }
        } finally {
            info.dispose();
        }
    }
    
    @Test
    public void testExpiration() throws Exception {
        VmsConfiguration config = createConfiguration();
        VmsConnector info = createConnector(config);

        try {
            Set<Attribute> attrs = fillInSampleUser(getTestUser());
    
            // Recreate the account
            //
            deleteUser(getTestUser(), info);
            info.create(ObjectClass.ACCOUNT, attrs, null);

            // Show that we can set noexpire
            //
            {
                ConnectorObjectBuilder builder = new ConnectorObjectBuilder();
                builder.setUid(getTestUser());
                builder.setName(getTestUser());
                Attribute expire = AttributeBuilder.build(OperationalAttributes.PASSWORD_EXPIRED_NAME, Boolean.FALSE);
                builder.addAttribute(expire);
                ConnectorObject newUser = builder.build();
                info.update(newUser.getObjectClass(), newUser.getAttributes(), null);
    
                ConnectorObject user = getUser(getTestUser());
                expire = user.getAttributeByName(OperationalAttributes.PASSWORD_EXPIRED_NAME);
                Assert.assertFalse(AttributeUtil.getBooleanValue(expire));
            }
            // Show that we can expire
            //
            {
                ConnectorObjectBuilder builder = new ConnectorObjectBuilder();
                builder.setUid(getTestUser());
                builder.setName(getTestUser());
                Attribute expire = AttributeBuilder.build(OperationalAttributes.PASSWORD_EXPIRED_NAME, Boolean.TRUE);
                builder.addAttribute(expire);
                ConnectorObject newUser = builder.build();
                info.update(newUser.getObjectClass(), newUser.getAttributes(), null);
    
                ConnectorObject user = getUser(getTestUser());
                expire = user.getAttributeByName(OperationalAttributes.PASSWORD_EXPIRED_NAME);
                Assert.assertTrue(AttributeUtil.getBooleanValue(expire));
            }
        } finally {
            info.dispose();
        }
    }
    
    @Test
    public void testExpirationDates() throws Exception {
        VmsConfiguration config = createConfiguration();
        VmsConnector info = createConnector(config);

        try {
            Set<Attribute> attrs = fillInSampleUser(getTestUser());
    
            // Recreate the account
            //
            deleteUser(getTestUser(), info);
            info.create(ObjectClass.ACCOUNT, attrs, null);

            // Show that we can set expiration in the future
            //
            {
                ConnectorObjectBuilder builder = new ConnectorObjectBuilder();
                builder.setUid(getTestUser());
                builder.setName(getTestUser());
                long future = new Date().getTime()+100000;
                Attribute expire = AttributeBuilder.build(OperationalAttributes.PASSWORD_EXPIRATION_DATE_NAME, future);
                builder.addAttribute(expire);
                ConnectorObject newUser = builder.build();
                info.update(newUser.getObjectClass(), newUser.getAttributes(), null);
    
                ConnectorObject user = getUser(getTestUser());
                expire = user.getAttributeByName(OperationalAttributes.PASSWORD_EXPIRATION_DATE_NAME);
                Object expireValue = AttributeUtil.getSingleValue(expire);
                long expireLong = ((Number)expireValue).longValue();
                Assert.assertTrue(expireLong > new Date().getTime());
            }
            // Show that we can expire (this should also for expired to be true)
            //
            //  This test is dummied out because the VMS machine is out of synch with respect
            //  to time.
            //
            if (false)
            {
                ConnectorObjectBuilder builder = new ConnectorObjectBuilder();
                builder.setUid(getTestUser());
                builder.setName(getTestUser());
                long now = new Date().getTime()+5000;
                Attribute expire = AttributeBuilder.build(OperationalAttributes.PASSWORD_EXPIRATION_DATE_NAME, now);
                builder.addAttribute(expire);
                ConnectorObject newUser = builder.build();
                info.update(newUser.getObjectClass(), newUser.getAttributes(), null);
                // Must delay to let password expire
                //
                Thread.sleep(60*1000);
                ConnectorObject user = getUser(getTestUser());
                expire = user.getAttributeByName(OperationalAttributes.PASSWORD_EXPIRED_NAME);
                Assert.assertTrue(AttributeUtil.getBooleanValue(expire));
                Attribute expireDate = user.getAttributeByName(OperationalAttributes.PASSWORD_EXPIRATION_DATE_NAME);
                Assert.assertTrue(AttributeUtil.getLongValue(expireDate) > (now-120000));
                Assert.assertTrue(AttributeUtil.getLongValue(expireDate) < new Date().getTime());
            }
            // Show that we can expire never (this should also for expired to be false)
            //
            {
                ConnectorObjectBuilder builder = new ConnectorObjectBuilder();
                builder.setUid(getTestUser());
                builder.setName(getTestUser());
                long never = 0;
                Attribute expire = AttributeBuilder.build(OperationalAttributes.PASSWORD_EXPIRATION_DATE_NAME, never);
                builder.addAttribute(expire);
                ConnectorObject newUser = builder.build();
                info.update(newUser.getObjectClass(), newUser.getAttributes(), null);
    
                ConnectorObject user = getUser(getTestUser());
                expire = user.getAttributeByName(OperationalAttributes.PASSWORD_EXPIRED_NAME);
                Assert.assertFalse(AttributeUtil.getBooleanValue(expire));
                Attribute expireDate = user.getAttributeByName(OperationalAttributes.PASSWORD_EXPIRATION_DATE_NAME);
                Assert.assertTrue(AttributeUtil.getIntegerValue(expireDate) == never);
            }
        } finally {
            info.dispose();
        }
    }
    
    @Test
    public void testBadAttributes() throws Exception {
        // Negative tests
        //
        testBadValue(AttributeBuilder.build(ATTR_ALGORITHM, "BOTH=BAD"));
        testBadValue(AttributeBuilder.build(ATTR_ALGORITHM, "CURRENT=CUSTOMER=12"));
        testBadValue(AttributeBuilder.build(ATTR_ALGORITHM, "CURRENT=CUSTOMER=999"));
        testBadValue(AttributeBuilder.build("XYZZY", "CURRENT=CUSTOMER=999"));
        List<String> badFlags = new LinkedList<String>();
        badFlags.add("UGLY");
        testBadValue(AttributeBuilder.build(ATTR_FLAGS, badFlags));
        testBadValue(AttributeBuilder.build(ATTR_DEFPRIVILEGES, badFlags));
        testBadValue(AttributeBuilder.build(ATTR_PRIVILEGES, badFlags));
    }

    private void testBadValue(Attribute attribute) throws Exception {
        VmsConfiguration config = createConfiguration();
        try {
            VmsAttributeValidator.validate(attribute.getName(), attribute.getValue(), config);
            Assert.fail("Attribute should not have been valid");
        } catch (RuntimeException rte) {
            // expected failure
        }
    }
    private void testModifyUserAttribute(VmsConnector connector, Attribute attribute) throws Exception {
        testModifyUserAttribute(connector, attribute, true);
    }

    private void testModifyUserAttribute(VmsConnector connector, Attribute attribute, boolean check) throws Exception {
        ConnectorObjectBuilder builder = new ConnectorObjectBuilder();
        builder.setUid(getTestUser());
        builder.setName(getTestUser());
        builder.addAttribute(attribute);
        ConnectorObject newUser = builder.build();
        connector.update(newUser.getObjectClass(), newUser.getAttributes(), null);

        if (check) {
            ConnectorObject user = getUser(connector, getTestUser());
            Attribute newAttribute = user.getAttributeByName(attribute.getName());
            Assert.assertEquals(attribute.getValue(), newAttribute.getValue());
        }
    }

    private ConnectorObject getUser(String accountId) throws Exception  {
        VmsConfiguration config = createConfiguration();
        VmsConnector info = createConnector(config);
        try {
            return getUser(info, accountId);
        } finally {
            info.dispose();
            info.dispose();
        }
    }

    private ConnectorObject getUser(VmsConnector connector, String accountId) throws Exception  {
        TestHandler handler = new TestHandler();
        TestHelpers.search(connector,ObjectClass.ACCOUNT, new EqualsFilter(AttributeBuilder.build(Name.NAME, getTestUser())), handler, null);
        return handler.iterator().next();
    }

    @Test
    public void testCreate() throws Exception {
        VmsConfiguration config = createConfiguration();
        VmsConnector info = createConnector(config);

        try {
            Set<Attribute> attrs = fillInSampleUser(getTestUser());
    
            // Delete the account if it already exists
            //
            deleteUser(getTestUser(), info);
    
            // Create the account
            //
            Uid newUid = info.create(ObjectClass.ACCOUNT, attrs, null);
            System.out.println(newUid.getValue()+" created");
            // Create the account
            //
            try {
                info.create(ObjectClass.ACCOUNT, attrs, null);
                Assert.fail("Didn't catch create existing user");
            } catch (AlreadyExistsException e) {
                // We expect this
            }
        } finally {
            info.dispose();
            info.dispose();
        }
    }

    @Test
    public void testChangePasswordAsAdmin() throws Exception {
        VmsConfiguration config = createConfiguration();
        VmsConnector info = createConnector(config);

        try {
            Set<Attribute> attrs = fillInSampleUser(getTestUser());
    
            // Delete the account if it already exists
            //
            deleteUser(getTestUser(), info);
    
            // Create the account
            //
            Uid newUid = info.create(ObjectClass.ACCOUNT, attrs, null);
            System.out.println(newUid.getValue()+" created");
    
            ConnectorObjectBuilder builder = new ConnectorObjectBuilder();
            builder.setUid(getTestUser());
            builder.setName(getTestUser());
            Attribute password = AttributeBuilder.build(OperationalAttributes.PASSWORD_NAME, new GuardedString("xyzzy123".toCharArray()));
            Attribute current_password = AttributeBuilder.build(OperationalAttributes.CURRENT_PASSWORD_NAME, new GuardedString("password".toCharArray()));
            builder.addAttribute(current_password);
            builder.addAttribute(password);
            ConnectorObject newUser = builder.build();
            info.update(newUser.getObjectClass(), newUser.getAttributes(), null);
        } finally {
            info.dispose();
            info.dispose();
        }
    }

    @Test
    public void testChangeOwnPassword() throws Exception {
        String localUserName = getTestUser()+"X";
        VmsConfiguration config = createConfiguration();
        VmsConnector info = createConnector(config);

        try {
            // Since we will actually log in, make sure all days are primary days
            //
            Set<Attribute> attrs = fillInSampleUser(localUserName, true);
    
            // Delete the account if it already exists
            //
            deleteUser(localUserName, info);
    
            // Create the account
            //
            Uid newUid = info.create(ObjectClass.ACCOUNT, attrs, null);
            System.out.println(newUid.getValue()+" created");
        } finally {
            info.dispose();
        }

        // Now, create a configuration for the user we created
        // and change password
        //
        VmsConfiguration userConfig = createUserConfiguration(localUserName);
        VmsConnector userInfo = createConnector(userConfig);
        try {
            ConnectorObjectBuilder builder = new ConnectorObjectBuilder();
            builder.setUid(localUserName);
            builder.setName(localUserName);
            Attribute password = AttributeBuilder.build(OperationalAttributes.PASSWORD_NAME, new GuardedString("xyzzy123".toCharArray()));
            Attribute current_password = AttributeBuilder.build(OperationalAttributes.CURRENT_PASSWORD_NAME, new GuardedString("password".toCharArray()));
            builder.addAttribute(current_password);
            builder.addAttribute(password);
            ConnectorObject newUser = builder.build();
            userInfo.update(newUser.getObjectClass(), newUser.getAttributes(), null);
        } finally {
            userInfo.dispose();
        }
    }

    private void deleteUser(final String testUser, VmsConnector connector) {
        try {
            connector.delete(ObjectClass.ACCOUNT, new Uid(testUser), null);
        } catch (UnknownUidException rte) {
            // Ignore
        }
    }

    @Test
    public void testSchema() throws Exception {
        VmsConfiguration config = createConfiguration();
        VmsConnector info = createConnector(config);
        Schema schema = info.schema();
        boolean accountSeen = false;
        Set<ObjectClassInfo> ocInfos = schema.getObjectClassInfo();
        for (ObjectClassInfo ocInfo : ocInfos) {
            if (ocInfo.getType().equals(ObjectClass.ACCOUNT_NAME)) {
                accountSeen = true;
            }
        }
        Assert.assertTrue(accountSeen);
    }

    @Test
    public void testDelta() throws Exception {
        testDelta(500, "0-0:0:0.50");
        testDelta(500+11*1000, "0-0:0:11.50");
        testDelta(500+12*1000+6*1000*60, "0-0:6:12.50");
        testDelta(500+12*1000+6*1000*60+60*60*1000, "0-1:6:12.50");
        testDelta(500+12*1000+6*1000*60+60*60*1000+3*24*60*60*1000, "3-1:6:12.50");
    }
    
    private void testDelta(long delta, String expectedValue) {
        List<Object> values = new LinkedList<Object>();
        values.add(delta);
        VmsConnector.remapToDelta(values);
        Assert.assertEquals(expectedValue, values.get(0));
    }

    @Test
    public void testDelete() throws Exception {
        VmsConfiguration config = createConfiguration();
        VmsConnector info = createConnector(config);

        try {
            Set<Attribute> attrs = fillInSampleUser(getTestUser());
    
            // Create the account if it doesn't already exist
            //
            if (getUser(getTestUser())==null) {
                try {
                    Uid newUid = info.create(ObjectClass.ACCOUNT, attrs, null);
                    System.out.println(newUid.getValue()+" created");
                } catch (RuntimeException rte) {
                    if (!(rte.getCause() instanceof AlreadyExistsException)) {
                        throw rte;
                    } 
                }
            }
    
            // Delete the account
            //
            Uid deleteUid = new Uid(getTestUser());
            info.delete(ObjectClass.ACCOUNT, deleteUid, null);
            System.out.println(deleteUid.getValue()+" deleted");
    
            // Now delete it a second time
            //
            try {
                info.delete(ObjectClass.ACCOUNT, deleteUid, null);
                Assert.fail("NO exception deleting non-existent user");
            } catch (UnknownUidException uue) {
                // Expected
            }
        } finally {
            info.dispose();
        }
    }

    private Set<Attribute> fillInSampleUser(final String testUser) {
        return fillInSampleUser(testUser, false);
    }

    private Set<Attribute> fillInSampleUser(final String testUser, boolean allDays) {
        Set<Attribute> attrs = new HashSet<Attribute>();
        attrs.add(AttributeBuilder.build(Name.NAME, testUser));
        List<String> primary_days = new LinkedList<String>();
        primary_days.add(DAYS_MON);
        primary_days.add(DAYS_TUE);
        primary_days.add(DAYS_THU);
        primary_days.add(DAYS_FRI);
        if (allDays) {
            primary_days.add(DAYS_WED);
            primary_days.add(DAYS_SAT);
            primary_days.add(DAYS_SUN);
        } else {
            primary_days.add(DAYS_NOT+DAYS_WED);
            primary_days.add(DAYS_NOT+DAYS_SAT);
            primary_days.add(DAYS_NOT+DAYS_SUN);
        }
        attrs.add(AttributeBuilder.build(VmsConstants.ATTR_PRIMEDAYS, primary_days));

        List<String> flags = new LinkedList<String>();
        flags.add(FLAG_NOT+FLAG_DISUSER);
        flags.add(FLAG_NOT+FLAG_PWD_EXPIRED);
        attrs.add(AttributeBuilder.build(VmsConstants.ATTR_FLAGS, flags));


        List<String> privs = new LinkedList<String>();
        privs.add(PRIV_NETMBX);
        privs.add(PRIV_TMPMBX);
        attrs.add(AttributeBuilder.build(VmsConstants.ATTR_PRIVILEGES, privs));

        List<GuardedString> password = new LinkedList<GuardedString>();
        password.add(new GuardedString("password".toCharArray()));
        attrs.add(AttributeBuilder.build(OperationalAttributes.PASSWORD_NAME, password));

        List<Object> expired = new LinkedList<Object>();
        expired.add(Boolean.FALSE);
        attrs.add(AttributeBuilder.build(OperationalAttributes.PASSWORD_EXPIRED_NAME, expired));

        Name name = new Name(testUser);
        attrs.add(name);
        
        attrs.add(AttributeBuilder.build(OperationalAttributes.PASSWORD_EXPIRATION_DATE_NAME, new Date(108, 12, 31).getTime()));

        return attrs;
    }

    private VmsConnector createConnector(VmsConfiguration config)
    throws Exception {
        VmsConnector rv = new VmsConnector();
        rv.init(config);
        return rv;
    }

    protected int getHostPort() {
        return 23;
    }

    protected Boolean isSSH() {
        return Boolean.FALSE;
    }
    
    protected String getTestUser() {
        return "TEST106";
    }

    protected String getTestUserEnd() {
        return "106";
    }

    protected String getConnectScript() {
        String script =
            "connection.waitFor(\"sername:\");\n" +
            "connection.send(username);\n" +
            "connection.waitFor(\"assword:\");\n" +
            "connection.send(password);\n" +
            "connection.waitFor(prompt);\n"
            ;
        return script;
    }

    private VmsConfiguration createConfiguration() {
        VmsConfiguration config = new VmsConfiguration();
        config.setHostLineTerminator(LINE_TERMINATOR);
        config.setHostNameOrIpAddr(HOST_NAME);
        config.setHostPortNumber(getHostPort());
        config.setHostShellPrompt(SHELL_PROMPT);
        config.setPassword(new GuardedString(SYSTEM_PASSWORD.toCharArray()));
        config.setUserName(SYSTEM_USER);
        config.setConnectScript(getConnectScript());
        config.setSSH(isSSH());
        return config;
    }


    private VmsConfiguration createUserConfiguration(String userName) {
        VmsConfiguration config = new VmsConfiguration();
        config.setHostLineTerminator(LINE_TERMINATOR);
        config.setHostNameOrIpAddr(HOST_NAME);
        config.setHostPortNumber(getHostPort());
        config.setHostShellPrompt(SHELL_PROMPT);
        config.setPassword(new GuardedString("password".toCharArray()));
        config.setUserName(userName);
        config.setConnectScript(getConnectScript());
        config.setSSH(isSSH());
        return config;
    }


    public static class TestHandler implements ResultsHandler, Iterable<ConnectorObject> {
        private List<ConnectorObject> objects = new LinkedList<ConnectorObject>();

        public boolean handle(ConnectorObject object) {
            objects.add(object);
            return true;
        }

        public Iterator<ConnectorObject> iterator() {
            return objects.iterator();
        }
    }
}