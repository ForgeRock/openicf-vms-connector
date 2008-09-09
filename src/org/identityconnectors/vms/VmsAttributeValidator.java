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

import static org.identityconnectors.vms.VmsConstants.*;

import java.lang.reflect.Method;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VmsAttributeValidator {
    private static class ValidatorInfo {
        enum ValidatorType {
            NUMERIC,
            PATTERN,
            METHOD
        };
        private ValidatorType 	_validatorType;
        private Object 			_value;
        private int				_multiplicity;

        public ValidatorInfo(int multiplicity) {
            _validatorType = ValidatorType.NUMERIC;
            _multiplicity = multiplicity;
        }

        public ValidatorInfo(Pattern pattern, int multiplicity) {
            _validatorType = ValidatorType.PATTERN;
            _value = pattern;
            _multiplicity = multiplicity;
        }

        public ValidatorInfo(Method method) {
            _validatorType = ValidatorType.METHOD;
            _value = method;
            // Multiplicity will be checked by the Method,
            // so we set it to be unchecked.
            //
            _multiplicity = -1; 
        }

        public ValidatorType getValidatorType() {
            return _validatorType;
        }

        public Pattern getPattern() {
            return (Pattern)_value;
        }

        public Method getMethod() {
            return (Method)_value;
        }

        public int getMultiplicity() {
            return _multiplicity;
        }
    }

    // Pattern to validate values
    //
    private static final Pattern _accountPattern 		= Pattern.compile("[a-zA-Z0-9]{1,8}"); 
    private static final Pattern _algorithmPattern 		= Pattern.compile("(\\w+)=(\\w+)(=\\d+)?"); 
    private static final Pattern _cliPattern 			= Pattern.compile("[a-zA-Z0-9$_:]{1,31}"); 
    private static final Pattern _cliTablesPattern 		= Pattern.compile("[a-zA-Z0-9$_:]{1,31}"); 
    private static final Pattern _deltaTimePattern 		= Pattern.compile("(\\d+-)?\\s*(\\d+(:\\d+){0,2})?(\\.\\d+)?"); 
    private static final Pattern _devicePattern 		= Pattern.compile("[a-zA-Z0-9:]{1,31}"); 
    private static final Pattern _directoryPattern 		= Pattern.compile("(\\[[a-zA-Z0-9:]{1,39}\\])|[a-zA-Z0-9:]{1,39}"); 
    private static final Pattern _fileSpecPattern 		= Pattern.compile("[a-zA-Z0-9$_:]+"); 
    private static final Pattern _maxDetachPattern 		= Pattern.compile("(NONE)|(\\d+)", Pattern.CASE_INSENSITIVE); 
    private static final Pattern _ownerPattern 			= Pattern.compile(".{1,31}"); 
    private static final Pattern _passwordPattern 		= null; 
    private static final Pattern _uicPattern 			= Pattern.compile("\\[[0-3][0-7][0-7],[0-3][0-7][0-7]\\]"); 

    private static Map<String, ValidatorInfo> VALIDATOR_INFO = new HashMap<String, ValidatorInfo>();

    static Method getMethodByName(String name) {
        try {
            return VmsAttributeValidator.class.getMethod(name, new Class[]{List.class});
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Determine if the string represents a valid VMS date stamp
     * 
     * @param date
     * @return
     */
    public static boolean isValidDate(List<String> dateList) {
        if (dateList.size()!=1)
            return false;
        String date = dateList.get(0);
        DateFormat dateFormat = new SimpleDateFormat("dd-MMM-yyyy", Locale.US);
        try {
            dateFormat.parse(date.trim());
            return true;
        } catch (ParseException e) {
            return false;
        }
    }
//    private static final Method IS_VALID_DATE = getMethodByName("isValidDate");

    /**
     * Determine if the string represents a valid VMS date stamp, or "none"
     * 
     * @param date
     * @return
     */
    public static boolean isValidDateOrNone(List<String> dateList) {
        if (dateList.size()!=1)
            return false;
        String date = dateList.get(0).trim().toUpperCase();
        return "NONE".equals(date) || isValidDate(dateList);
    }
    private static final Method IS_VALID_DATE_OR_NONE = getMethodByName("isValidDateOrNone");

    /**
     * Determine if the value for ACCESS is valid.
     * <pre>
     *          /ACCESS[=(range[,...])]
     *          /NOACCESS[=(range[,...])]
     *
     *       Specifies hours of access for all modes of access. The syntax for
     *       specifying the range is:
     *
     *    UAF> /[NO]ACCESS=([PRIMARY],[n-m],[n],[,...],[SECONDARY],[n-m],[n],[,...])
     *
     *       Specify hours as integers from 0 to 23, inclusive. You can
     *       specify single hours (n)  or ranges of hours (n-m). If the ending
     *       hour of a range is earlier than the starting hour, the range
     *       extends from the starting hour through midnight to the ending
     *       hour. The first set of hours after the keyword PRIMARY specifies
     *       hours on primary days; the second set of hours after the keyword
     *       SECONDARY specifies hours on secondary days. Note that hours
     *       are inclusive; that is, if you grant access during a given hour,
     *       access extends to the end of that hour.
     *
     *       By default, a user has full access every day. See the DCL command
     *       SET DAY in the HP OpenVMS DCL Dictionary for information about
     *       overriding the defaults for primary and secondary day types.
     *
     *       All the list elements are optional. Unless you specify hours for
     *       a day type, access is permitted for the entire day. By specifying
     *       an access time, you prevent access at all other times. Adding
     *       NO to the qualifier denies the user access to the system for the
     *       specified period of time. See the following examples.
     *
     *       /ACCESS                Allows unrestricted access
     *
     *       /NOACCESS=SECONDARY    Allows access on primary days only
     *
     *       /ACCESS=(9-17)         Allows access from 9 A.M. to 5:59 P.M. on
     *                              all days
     *
     *       /NOACCESS=(PRIMARY,    Disallows access between 9 A.M. to 5:59
     *       9-17, SECONDARY,       P.M. on primary days but allows access
     *       18-8)                  during these hours on secondary days
     *
     *       To specify access hours for specific types of access, see the
     *       /BATCH, /DIALUP, /INTERACTIVE, /LOCAL, /NETWORK, and /REMOTE
     *       qualifiers.
     *
     *       Refer to HP OpenVMS Guide to System Security for information
     *       about the effects of login class restrictions.
     * </pre>
     *
     * @param algorithm
     */
    public static boolean isValidAccessList(List<String> accessList) {
        for (String access : accessList) {
            access = access.trim().toUpperCase();
            if (ACCESS_PRIMARY.equals(access))
                return true;
            if (ACCESS_SECONDARY.equals(access))
                return true;
            try {
                int accessInt = Integer.parseInt(access);
                return (accessInt>=0 && accessInt<=23);
            } catch (NumberFormatException e) {
                return false;
            } 
        }
        return true;
    }
    private static final Method IS_VALID_ACCESS_LIST = getMethodByName("isValidAccessList");

    /**
     *  Determine if the value for ALGORITHM is valid.
     * <pre>
     *          /ALGORITHM=keyword=type [=value]
     * 
     *       Sets the password encryption algorithm for a user. The keyword
     *       VMS refers to the algorithm used in the operating system version
     *       that is running on your system, whereas a customer algorithm is
     *       one that is added through the $HASH_PASSWORD system service by
     *       a customer site, by a layered product, or by a third party. The
     *       customer algorithm is identified in $HASH_PASSWORD by an integer
     *       in the range of 128 to 255. It must correspond with the number
     *       used in the AUTHORIZE command MODIFY/ALGORITHM. By default,
     *       passwords are encrypted with the VMS algorithm for the current
     *       version of the operating system.
     *
     *       Keyword     Function
     *
     *       BOTH        Set the algorithm for primary and secondary
     *                   passwords.
     *
     *       CURRENT     Set the algorithm for the primary, secondary, both,
     *                   or no passwords, depending on account status. CURRENT
     *                   is the default value.
     *
     *       PRIMARY     Set the algorithm for the primary password only.
     *
     *       SECONDARY   Set the algorithm for the secondary password only.
     *
     *       The following table lists password encryption algorithms:
     *
     *       Type        Definition
     *
     *       VMS         The algorithm used in the version of the operating
     *                   system that is running on your system.
     *
     *       CUSTOMER    A numeric value in the range of 128 to 255 that
     *                   identifies a customer algorithm.
     *
     *       The following example selects the VMS algorithm for Sontag's
     *       primary password:
     *
     *       UAF>  MODIFY SONTAG/ALGORITHM=PRIMARY=VMS
     *
     *       If you select a site-specific algorithm, you must give a value to
     *       identify the algorithm, as follows:
     *
     *       UAF>  MODIFY SONTAG/ALGORITHM=CURRENT=CUSTOMER=128
     * </pre>
     *
     * @param algorithm
     * @return
     */
    public static boolean isValidAlgorithm(List<String> algorithmList) {
        if (algorithmList.size()!=1)
            return false;
        String algorithm = algorithmList.get(0).trim();
        Matcher matcher = _algorithmPattern.matcher(algorithm);
        if (matcher.matches()) {
            String keyword = matcher.group(1).trim().toUpperCase();
            String type    = matcher.group(2).trim().toUpperCase();
            String userval = null;
            if (matcher.group(3)!=null)
                userval = matcher.group(3).trim();
            else
                userval = "";
            if (ALGO_KEYS_LIST.contains(keyword)) {
                if (ALGO_TYPE_VMS.equals(type)) {
                    if (userval.length()==0)
                        return true;
                } else if (ALGO_TYPE_CUSTOMER.equals(type) && userval.length()>0) {
                    int value = Integer.parseInt(userval.substring(1));
                    if (value>=128 && value<=255)
                        return true;
                }
            }

        }
        return false;
    }
    private static final List<String> ALGO_KEYS_LIST = makeList(new String[] {ALGO_KEY_BOTH, ALGO_KEY_CURRENT, ALGO_KEY_PRIMARY, ALGO_KEY_SECONDARY });
    private static final Method IS_VALID_ALGORITHM = getMethodByName("isValidAlgorithm");

    /**
     *  Determine if the value for FLAG(s) is valid.
     * <pre>
     *          /FLAGS=([NO]option[,...])
     *
     *       Specifies login flags for the user. The prefix NO clears the
     *       flag. The options are as follows:
     *
     *       AUDIT        Enables or disables mandatory security auditing for
     *                    a specific user. By default, the system does not
     *                    audit the activities of specific users (NOAUDIT).
     *
     *       AUTOLOGIN    Restricts the user to the automatic login mechanism
     *                    when logging in to an account. When set, the flag
     *                    disables login by any terminal that requires entry
     *                    of a user name and password. The default is to
     *                    require a user name and password (NOAUTOLOGIN).
     *
     *       CAPTIVE      Prevents the user from changing any defaults at
     *                    login, for example, /CLI or /LGICMD. It prevents
     *                    the user from escaping the captive login command
     *                    procedure specified by the /LGICMD qualifier and
     *                    gaining access to the DCL command level. Refer to
     *                    "Guidelines for Captive Command Procedures" in the
     *                    HP OpenVMS Guide to System Security.
     *
     *                    The CAPTIVE flag also establishes an environment
     *                    where Ctrl/Y interrupts are initially turned off;
     *                    however, command procedures can still turn on Ctrl/Y
     *                    interrupts with the DCL command SET CONTROL=Y. By
     *                    default, an account is not captive (NOCAPTIVE).
     *
     *       DEFCLI       Restricts the user to the default command
     *                    interpreter by prohibiting the use of the /CLI
     *                    qualifier at login. By default, a user can choose
     *                    a CLI (NODEFCLI).
     *
     *       DISCTLY      Establishes an environment where Ctrl/Y interrupts
     *                    are initially turned off and are invalid until a
     *                    SET CONTROL=Y is encountered. This could happen in
     *                    SYLOGIN.COM or in a procedure called by SYLOGIN.COM.
     *                    Once a SET CONTROL=Y is executed (which requires
     *                    no privilege), a user can enter a Ctrl/Y and reach
     *                    the DCL prompt ($).  If the intent of DISCTLY is
     *                    to force execution of the login command files,
     *                    then SYLOGIN.COM should issue the DCL command
     *                    SET CONTROL=Y to turn on Ctrl/Y interrupts before
     *                    exiting. By default, Ctrl/Y is enabled (NODISCTLY).
     *
     *       DISFORCE_    Removes the requirement that a user must change an
     *       PWD_CHANGE   expired password at login. By default, a person can
     *                    use an expired password only once (NODISFORCE_PWD_
     *                    CHANGE) and then is forced to change the password
     *                    after logging in. If the user does not select a new
     *                    password, the user is locked out of the system.
     *
     *                    To use this feature, set a password expiration date
     *                    with the /PWDLIFETIME qualifier.
     *
     *       DISIMAGE     Prevents the user from executing RUN and foreign
     *                    commands. By default, a user can execute RUN and
     *                    foreign commands (NODISIMAGE).
     *
     *       DISMAIL      Disables mail delivery to the user. By default, mail
     *                    delivery is enabled (NODISMAIL).
     *
     *       DISNEWMAIL   Suppresses announcements of new mail at login.
     *                    By default, the system announces new mail
     *                    (NODISNEWMAIL).
     *
     *       DISPWDDIC    Disables automatic screening of new passwords
     *                    against a system dictionary. By default, passwords
     *                    are automatically screened (NODISPWDDIC).
     *
     *       DISPWDHIS    Disables automatic checking of new passwords against
     *                    a list of the user's old passwords. By default, the
     *                    system screens new passwords (NODISPWDHIS).
     *
     *       DISPWDSYNCH  Suppresses synchronization of the external password
     *                    for this account. See bit 9 in the SECURITY_
     *                    POLICY system parameter for systemwide password
     *                    synchronization control.
     *
     *       DISRECONNECT Disables automatic reconnection to an existing
     *                    process when a terminal connection has been
     *                    interrupted. By default, automatic reconnection
     *                    is enabled (NODISRECONNECT).
     *
     *       DISREPORT    Suppresses reports of the last login time, login
     *                    failures, and other security reports. By default,
     *                    login information is displayed (NODISREPORT).
     *
     *       DISUSER      Disables the account so the user cannot log in.
     *                    For example, the DEFAULT account is disabled. By
     *                    default, an account is enabled (NODISUSER).
     *
     *       DISWELCOME   Suppresses the welcome message (an informational
     *                    message displayed during a local login). This
     *                    message usually indicates the version number of
     *                    the operating system that is running and the name of
     *                    the node on which the user is logged in. By default,
     *                    a system login message appears (NODISWELCOME).
     *
     *       EXTAUTH      Considers user to be authenticated by an external
     *                    user name and password, not by the SYSUAF user name
     *                    and password. (The system still uses the SYSUAF
     *                    record to check a user's login restrictions and
     *                    quotas and to create the user's process profile.)
     *
     *       GENPWD       Restricts the user to generated passwords.
     *                    By default, users choose their own passwords
     *                    (NOGENPWD).
     *
     *       LOCKPWD      Prevents the user from changing the password for
     *                    the account. By default, users can change their
     *                    passwords (NOLOCKPWD).
     *
     *       PWD_EXPIRED  Marks a password as expired. The user cannot log in
     *                    if this flag is set. The LOGINOUT.EXE image sets the
     *                    flag when both of the following conditions exist: a
     *                    user logs in with the DISFORCE_PWD_CHANGE flag set,
     *                    and the user's password expires. A system manager
     *                    can clear this flag. By default, passwords are not
     *                    expired after login (NOPWD_EXPIRED).
     *
     *       PWD2_        Marks a secondary password as expired. Users cannot
     *       EXPIRED      log in if this flag is set. The LOGINOUT.EXE image
     *                    sets the flag when both of the following conditions
     *                    exist: a user logs in with the DISFORCE_PWD_CHANGE
     *                    flag set, and the user's password expires. A system
     *                    manager can clear this flag. By default, passwords
     *                    are not set to expire after login (NOPWD2_EXPIRED).
     *
     *       PWDMIX       Enables case-sensitive and extended-character
     *                    passwords.
     *
     *                    After PWDMIX is specified, you can then use mixed-
     *                    case and extended characters in passwords. Be aware
     *                    that before the PWDMIX flag is enabled, the system
     *                    stores passwords in all upper-case. Therefore, until
     *                    you change passwords, you must enter your pre-PWDMIX
     *                    passwords in upper-case.
     *
     *                    To change the password after PWDMIX is enabled:
     *
     *                    o  You (the user) can use the DCL command SET
     *                       PASSWORD, specifying the new mixed-case password
     *                       (omitting quotation marks).
     *
     *                    o  You (the system manager) can use the AUTHORIZE
     *                       command MODIFY/PASSWORD, and enclose the user's
     *                       new mixed-case password in quotation marks " ".
     *
     *       RESTRICTED   Prevents the user from changing any defaults at
     *                    login (for example, by specifying /LGICMD) and
     *                    prohibits user specification of a CLI with the
     *                    /CLI qualifier. The RESTRICTED flag establishes
     *                    an environment where Ctrl/Y interrupts are initially
     *                    turned off; however, command procedures can still
     *                    turn on Ctrl/Y interrupts with the DCL command SET
     *                    CONTROL=Y. Typically, this flag is used to prevent
     *                    an applications user from having unrestricted access
     *                    to the CLI. By default, a user can change defaults
     *                    (NORESTRICTED).
     *
     *       VMSAUTH      Allows account to use standard (SYSUAF)
     *                    authentication when the EXTAUTH flag would otherwise
     *                    require external authentication. This depends on the
     *                    application. An application specifies the VMS domain
     *                    of interpretation when calling SYS$ACM to request
     *                    standard VMS authentication for a user account that
     *                    normally uses external authentication.
     * </pre>
     * @param flag
     * @return
     */
    public static boolean isValidFlagList(List<String> flagList) {
        return isValidList(flagList, FLAGS_LIST);
    }
    private static final Method IS_VALID_FLAG_LIST = getMethodByName("isValidFlagList");
    private static final String[] FLAGS_ARRAY = {
        FLAG_AUDIT, FLAG_AUTOLOGIN, FLAG_CAPTIVE, FLAG_DEFCLI, FLAG_DISCTLY,
        FLAG_DISFORCE_PWD_CHANGE, FLAG_DISIMAGE, FLAG_DISMAIL, FLAG_DISNEWMAIL,
        FLAG_DISPWDDIC, FLAG_DISPWDHIS, FLAG_DISPWDSYNCH, FLAG_DISRECONNECT,
        FLAG_DISREPORT, FLAG_DISUSER, FLAG_DISWELCOME, FLAG_EXTAUTH, FLAG_GENPWD,
        FLAG_LOCKPWD, FLAG_PWD2_EXPIRED, FLAG_PWD_EXPIRED, FLAG_PWDMIX,
        FLAG_RESTRICTED, FLAG_VMSAUTH,
    };
    private static final List<String> FLAGS_LIST = makeList(FLAGS_ARRAY);

    /**
     * <pre>
     *          /GENERATE_PASSWORD[=keyword]
     *          /NOGENERATE_PASSWORD (default)
     *
     *       Invokes the password generator to create user passwords.
     *       Generated passwords can consist of 1 to 10 characters. Specify
     *       one of the following keywords:
     *
     *       BOTH       Generate primary and secondary passwords.
     *
     *       CURRENT    Do whatever the DEFAULT account does (for example,
     *                  generate primary, secondary, both, or no passwords).
     *                  This is the default keyword.
     *
     *       PRIMARY    Generate primary password only.
     *
     *       SECONDARY  Generate secondary password only.
     *
     *       When you modify a password, the new password expires
     *       automatically; it is valid only once (unless you specify
     *       /NOPWDEXPIRED). On login, users are forced to change their
     *       passwords (unless you specify /FLAGS=DISFORCE_PWD_CHANGE).
     *
     *       Note that the /GENERATE_PASSWORD and /PASSWORD qualifiers are
     *       mutually exclusive.
     * </pre>
     * @param passwordTypeList
     * @return
     */
    public static boolean isValidGeneratePassword(List<String> passwordTypeList) {
        if (passwordTypeList.size()!=1)
            return false;
        String passwordType = passwordTypeList.get(0).trim().toUpperCase();
        return PWD_TYPE_LIST.contains(passwordType);
    }
    private static final Method IS_VALID_GENERATE_PASSWORD = getMethodByName("isValidGeneratePassword");
    private static final String[] PWD_TYPE_ARRAY = {
        PWD_TYPE_BOTH, PWD_TYPE_CURRENT, PWD_TYPE_PRIMARY, PWD_TYPE_SECONDARY, 
    };	
    private static final List<String> PWD_TYPE_LIST = makeList(PWD_TYPE_ARRAY);

    /**
     * 
     * @param privList
     * @return
     */
    public static boolean isValidPrivList(List<String> privList) {
        return isValidList(privList, PRIVS_LIST);
    }
    private static final Method IS_VALID_PRIV_LIST = getMethodByName("isValidPrivList");
    private static final String[] PRIVS_ARRAY = {
        PRIV_ACNT, PRIV_ALLSPOOL, PRIV_ALTPRI, PRIV_AUDIT, PRIV_BUGCHK, 
        PRIV_BYPASS, PRIV_CMEXEC, PRIV_CMKRNL, PRIV_DIAGNOSE, PRIV_DOWNGRADE,
        PRIV_EXQUOTA, PRIV_GROUP, PRIV_GRPNAM, PRIV_GRPPRV, PRIV_IMPERSONATE,
        PRIV_IMPORT, PRIV_LOG_IO, PRIV_MOUNT, PRIV_NETMBX, PRIV_OPER, PRIV_PFNMAP,
        PRIV_PHY_IO, PRIV_PRMCEB, PRIV_PRMGBL, PRIV_PRMMBX, PRIV_PSWAPM,
        PRIV_READALL, PRIV_SECURITY, PRIV_SETPRV, PRIV_SHARE, PRIV_SHMEM,
        PRIV_SYSGBL, PRIV_SYSLCK, PRIV_SYSNAM, PRIV_SYSPRV, PRIV_TMPMBX,
        PRIV_UPGRADE, PRIV_VOLPRO, PRIV_WORLD, PRIV_WORLD
    };
    private static final List<String> PRIVS_LIST = makeList(PRIVS_ARRAY);

    /**
     * 
     * @param privList
     * @return
     */
    public static boolean isValidPrimeDaysList(List<String> primeDaysList) {
        return isValidList(primeDaysList, PRIMEDAYS_LIST);
    }
    private static final Method IS_VALID_PRIME_DAYS_LIST = getMethodByName("isValidPrimeDaysList");
    private static final String[] PRIMEDAYS_ARRAY = {
        DAYS_SUN, DAYS_MON, DAYS_TUE, DAYS_WED, DAYS_THU, DAYS_FRI, DAYS_SAT
    };
    private static final List<String> PRIMEDAYS_LIST = makeList(PRIMEDAYS_ARRAY);

    public static boolean isValidList(List<String> valueList, List<String> validList) {
        for (String value : valueList) {
            value = value.trim();
            if (!validList.contains(value))
                if (!value.startsWith(NO) || !validList.contains(value.substring(2)))
                    return false;
        }
        return true;
    }

    private static LinkedList<String> makeList(String[] strings) {
        LinkedList<String> list = new LinkedList<String>();
        for (String string : strings) 
            list.add(string);
        return list;
    }

    static {
        VALIDATOR_INFO.put(ATTR_ACCESS, new ValidatorInfo(IS_VALID_ACCESS_LIST));
        VALIDATOR_INFO.put(ATTR_ALGORITHM, new ValidatorInfo(IS_VALID_ALGORITHM));
        VALIDATOR_INFO.put(ATTR_ACCOUNT, new ValidatorInfo(_accountPattern, 1));
        VALIDATOR_INFO.put(ATTR_ASTLM, new ValidatorInfo(1));
        VALIDATOR_INFO.put(ATTR_BATCH, new ValidatorInfo(IS_VALID_ACCESS_LIST));
        VALIDATOR_INFO.put(ATTR_BIOLM, new ValidatorInfo(1));
        VALIDATOR_INFO.put(ATTR_BYTLM, new ValidatorInfo(1));
        VALIDATOR_INFO.put(ATTR_CLI, new ValidatorInfo(_cliPattern, 1));
        VALIDATOR_INFO.put(ATTR_CLITABLES, new ValidatorInfo(_cliTablesPattern, 1));
        //VALIDATOR_INFO.put(ATTR_CPUTIME, new ValidatorInfo(_cliTablesPattern, 1));
        VALIDATOR_INFO.put(ATTR_DEFPRIVILEGES, new ValidatorInfo(IS_VALID_PRIV_LIST));
        VALIDATOR_INFO.put(ATTR_DEVICE, new ValidatorInfo(_devicePattern, 1));
        VALIDATOR_INFO.put(ATTR_DIALUP, new ValidatorInfo(IS_VALID_ACCESS_LIST));
        VALIDATOR_INFO.put(ATTR_DIOLM, new ValidatorInfo(1));
        VALIDATOR_INFO.put(ATTR_DIRECTORY, new ValidatorInfo(_directoryPattern, 1));
        VALIDATOR_INFO.put(ATTR_EXPIRATION, new ValidatorInfo(IS_VALID_DATE_OR_NONE));
        VALIDATOR_INFO.put(ATTR_FILLM, new ValidatorInfo(1));
        VALIDATOR_INFO.put(ATTR_FLAGS, new ValidatorInfo(IS_VALID_FLAG_LIST));
        VALIDATOR_INFO.put(ATTR_GENERATE_PASSWORD, new ValidatorInfo(IS_VALID_GENERATE_PASSWORD));
        VALIDATOR_INFO.put(ATTR_INTERACTIVE, new ValidatorInfo(IS_VALID_ACCESS_LIST));
        VALIDATOR_INFO.put(ATTR_JTQUOTA, new ValidatorInfo(1));
        VALIDATOR_INFO.put(ATTR_LGICMD, new ValidatorInfo(_fileSpecPattern, 1));
        VALIDATOR_INFO.put(ATTR_LOCAL, new ValidatorInfo(IS_VALID_ACCESS_LIST));
        VALIDATOR_INFO.put(ATTR_MAXACCTJOBS, new ValidatorInfo(1));
        VALIDATOR_INFO.put(ATTR_MAXDETACH, new ValidatorInfo(_maxDetachPattern, 1));
        VALIDATOR_INFO.put(ATTR_MAXJOBS, new ValidatorInfo(1));
        VALIDATOR_INFO.put(ATTR_NETWORK, new ValidatorInfo(IS_VALID_ACCESS_LIST));
        VALIDATOR_INFO.put(ATTR_OWNER, new ValidatorInfo(_ownerPattern, 1));
        VALIDATOR_INFO.put(ATTR_PASSWORD, new ValidatorInfo(_passwordPattern, 1));
        VALIDATOR_INFO.put(ATTR_PBYTLM, new ValidatorInfo(1));
        VALIDATOR_INFO.put(ATTR_PGFLQUOTA, new ValidatorInfo(1));
        VALIDATOR_INFO.put(ATTR_PRCLM, new ValidatorInfo(1));
        VALIDATOR_INFO.put(ATTR_PRIMEDAYS, new ValidatorInfo(IS_VALID_PRIME_DAYS_LIST));
        VALIDATOR_INFO.put(ATTR_PRIORITY, new ValidatorInfo(1));
        VALIDATOR_INFO.put(ATTR_PRIVILEGES, new ValidatorInfo(IS_VALID_PRIV_LIST));
        VALIDATOR_INFO.put(ATTR_PWDEXPIRED, new ValidatorInfo(0));
        VALIDATOR_INFO.put(ATTR_PWDLIFETIME, new ValidatorInfo(_deltaTimePattern, 1));
        VALIDATOR_INFO.put(ATTR_PWDMINIMUM, new ValidatorInfo(1));
        VALIDATOR_INFO.put(ATTR_QUEPRIO, new ValidatorInfo(1));
        VALIDATOR_INFO.put(ATTR_REMOTE, new ValidatorInfo(IS_VALID_ACCESS_LIST));
        VALIDATOR_INFO.put(ATTR_SHRFILLM, new ValidatorInfo(1));
        VALIDATOR_INFO.put(ATTR_TQELM, new ValidatorInfo(1));
        VALIDATOR_INFO.put(ATTR_UIC, new ValidatorInfo(_uicPattern, 1));
        VALIDATOR_INFO.put(ATTR_WSDEFAULT, new ValidatorInfo(1));
        VALIDATOR_INFO.put(ATTR_WSEXTENT, new ValidatorInfo(1));
        VALIDATOR_INFO.put(ATTR_WSQUOTA, new ValidatorInfo(1));
    }

    /**
     * Determine whether the Attribute has a valid value.
     * 
     * @param attribute
     */
    public static void validate(String name, List<Object> values, VmsConfiguration vmsConfiguration) {
        name = name.trim().toUpperCase();

        ValidatorInfo validatorInfo = VALIDATOR_INFO.get(name);

        // If the attribute is negated, it does not need a value
        //
        if (validatorInfo==null) {
            if (name.startsWith("NO")) {
                name = name.substring(2);
                validatorInfo = VALIDATOR_INFO.get(name);
                if (validatorInfo!=null)
                    if (values.size()==0)
                        return;
            }
        }

        // Ensure we have an attribute of this name
        //
        if (validatorInfo==null)
            throw new IllegalArgumentException(vmsConfiguration.getMessage(VmsMessages.UNKNOWN_ATTR_NAME, name));

        // Ensure the multiplicity is correct
        //
        int multiplicity = validatorInfo.getMultiplicity();
        if (multiplicity>=0)
            if (values.size()!=multiplicity)
                throw new IllegalArgumentException(vmsConfiguration.getMessage(VmsMessages.INVALID_ATTR_MULTIPLICITY, name));

        // Ensure the values are valid
        //
        switch (validatorInfo.getValidatorType()) {
        case NUMERIC:
            for (Object value : values)
                if (!(value instanceof Number))
                    throw new IllegalArgumentException(vmsConfiguration.getMessage(VmsMessages.INVALID_ATTR_VALUE, value, name));
            break;
        case PATTERN:
            for (Object value : values)
                if (validatorInfo.getPattern()!=null) {
                    if (!(value instanceof String)) {
                        throw new IllegalArgumentException(vmsConfiguration.getMessage(VmsMessages.INVALID_ATTR_VALUE, value, name));
                    } else {
                        Matcher matcher = validatorInfo.getPattern().matcher((String)value);
                        if (!matcher.matches())
                            throw new IllegalArgumentException(vmsConfiguration.getMessage(VmsMessages.INVALID_ATTR_VALUE, value, name));
                    }
                }
            break;
        case METHOD:
            try {
                Method method = validatorInfo.getMethod();
                if (!((Boolean) method.invoke(null, values)).booleanValue())
                    throw new IllegalArgumentException(vmsConfiguration.getMessage(VmsMessages.INVALID_ATTR_VALUE, values, name));
            } catch (Exception e) {
                throw new IllegalArgumentException(vmsConfiguration.getMessage(VmsMessages.EXCEPTION_IN_ATTR, name), e);
            }
            break;
        }
    }
}