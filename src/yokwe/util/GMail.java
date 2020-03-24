package yokwe.util;

import java.io.File;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

import javax.json.JsonObject;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import org.slf4j.LoggerFactory;

import yokwe.UnexpectedException;
import yokwe.util.FileUtil;
import yokwe.util.json.JSONBase;

public class GMail extends JSONBase {
	static final org.slf4j.Logger logger = LoggerFactory.getLogger(GMail.class);

	public static final String PATH_DIR = "tmp/gmail";
	
	public static final String DEFAULT_NAME = "DEFAULT";
	
	public static String getPath(String name) {
		return String.format("%s/%s", PATH_DIR, name);
	}
	
	public static GMail load(String name) {
		File file = new File(getPath(name));
		if (!file.canRead()) {
			logger.error("Cannot read file");
			logger.error("  file  {}", file.getPath());
			throw new UnexpectedException("Cannot read file");
		}
		String jsonString = FileUtil.read().file(file);
		return JSONBase.getInstance(GMail.class, jsonString);
	}
	public static void save(String name, GMail value) {
		File file = new File(getPath(name));
		String jsonString = value.toJSONString();
		FileUtil.write().file(file, jsonString);
	}
	
	public String username;
	public String password;
	public String recipient;
	
	public Map<String, String> config;
	
	public GMail() {
		this.username = null;
		this.password = null;
		this.config   = new TreeMap<>();
	}
	
	public GMail(JsonObject jsonObject) {
		super(jsonObject);
	}
	
	public static void sendMessage(GMail gmail, String subject, String text) {
        Properties prop = new Properties();
        gmail.config.forEach((k, v) -> prop.put(k, v));
                
        Session session = Session.getInstance(prop,
            new javax.mail.Authenticator() {
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(gmail.username, gmail.password);
                }
            });

        try {
            Message message = new MimeMessage(session);
            
            message.setFrom(new InternetAddress(gmail.username));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(gmail.recipient));
            message.setSubject(subject);
            message.setText(text);

            Transport.send(message);

        } catch (MessagingException e) {
			String exceptionName = e.getClass().getSimpleName();
			logger.error("{} {}", exceptionName, e);
			throw new UnexpectedException(exceptionName, e);
        }
	}
	public static void sendMessage(String subject, String text) {
		GMail gmail = GMail.load(DEFAULT_NAME);
		sendMessage(gmail, subject, text);
	}
	
	public static void writeDefaultAccount() {
		GMail account = new GMail();
		
		account.username = "hasegawa.yasuhiro@gmail.com";
		// FIXME supply actual password
		account.password = "XXX";
		account.recipient = "hasegawa.yasuhiro+ubuntu-dev@gmail.com";
		
		account.config.put("mail.smtp.host", "smtp.gmail.com");
		account.config.put("mail.smtp.port", "587");
		account.config.put("mail.smtp.auth", "true");
		account.config.put("mail.smtp.starttls.enable", "true");
		
		GMail.save(GMail.DEFAULT_NAME, account);
		GMail copy = GMail.load(GMail.DEFAULT_NAME);

		logger.info("toString   {}", account.toJSONString());
		logger.info("copyString {}", copy.toJSONString());
	}

	public static void main(String[] args) {
		logger.info("START");
		
//		writeDefaultAccount();
		
		logger.info("STOP");		
	}
}