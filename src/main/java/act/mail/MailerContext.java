package act.mail;

import act.Act;
import act.app.App;
import act.util.ActContext;
import act.view.Template;
import act.view.ViewManager;
import org.osgl.http.H;
import org.osgl.storage.ISObject;
import org.osgl.storage.impl.SObject;
import org.osgl.util.C;
import org.osgl.util.E;
import org.osgl.util.S;

import javax.mail.Message;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.internet.*;
import java.io.File;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MailerContext extends ActContext.ActContextBase<MailerContext> {

    private H.Format fmt = H.Format.html;
    private InternetAddress from;
    private String subject;
    private List<InternetAddress> to = C.newList();
    private List<InternetAddress> cc = C.newList();
    private List<InternetAddress> bcc = C.newList();
    private String confId;
    private List<ISObject> attachments = C.newList();
    private String senderPath; // e.g. com.mycorp.myapp.mailer.AbcMailer.foo

    public MailerContext(App app, String confId) {
        super(app);
        this.confId = confId;
    }

    public MailerContext configId(String id) {
        confId = id;
        return this;
    }

    public String senderPath() {
        return senderPath;
    }

    public MailerContext senderPath(String path) {
        senderPath = path;
        return this;
    }

    public MailerContext senderPath(String className, String methodName) {
        senderPath = S.builder(className).append(".").append(methodName).toString();
        return this;
    }

    public MailerConfig mailerConfig() {
        return app().mailerConfigManager().config(confId);
    }

    @Override
    public MailerContext accept(H.Format fmt) {
        E.NPE(fmt);
        this.fmt = fmt;
        return this;
    }

    @Override
    public H.Format accept() {
        return null != fmt ? fmt : mailerConfig().contentType();
    }


    /**
     * If {@link #templatePath(String) template path has been set before} then return
     * the template path. Otherwise returns the {@link #senderPath()}
     * @return either template path or action path if template path not set before
     */
    public String templatePath() {
        String path = super.templatePath();
        if (S.notBlank(path)) {
            return path;
        } else {
            return senderPath().replace('.', '/');
        }
    }

    @Override
    public MailerContext templatePath(String templatePath) {
        return super.templatePath(templatePath);
    }

    @Override
    public <T> T renderArg(String name) {
        return super.renderArg(name);
    }

    @Override
    public MailerContext renderArg(String name, Object val) {
        return super.renderArg(name, val);
    }

    @Override
    public Map<String, Object> renderArgs() {
        return super.renderArgs();
    }

    /**
     * Called by bytecode enhancer to set the name list of the render arguments that is update
     * by the enhancer
     * @param names the render argument names separated by ","
     * @return this AppContext
     */
    public MailerContext __appRenderArgNames(String names) {
        return renderArg("__arg_names__", C.listOf(names.split(",")));
    }

    public List<String> __appRenderArgNames() {
        return renderArg("__arg_names__");
    }

    public H.Format contentType() {
        return accept();
    }

    public String subject() {
        return null != subject ? subject : mailerConfig().subject();
    }

    public MailerContext subject(String subject) {
        this.subject = subject;
        return this;
    }

    @Override
    public Locale locale() {
        return config().locale();
    }

    public MailerContext attach(ISObject... sobjs) {
        attachments.addAll(C.listOf(sobjs));
        return this;
    }

    public MailerContext attach(File... files) {
        for (File file : files) {
            attachments.add(SObject.of(file));
        }
        return this;
    }

    public MailerContext from(String from) {
        E.illegalArgumentIf(S.empty(from), "<from> cannot be empty");
        List<InternetAddress> l = canonicalRecipients(null, from);
        E.illegalArgumentIf(l.isEmpty(), "from address expected");
        if (l.size() > 1) {
            logger.warn("There are more than one email address specified, only the first one will be used as From address");
        }
        this.from = l.get(0);
        return this;
    }

    public InternetAddress from() {
        if (null == from) {
            return mailerConfig().from();
        }
        return from;
    }

    /**
     * Set to recipients
     *
     * @param recipients the list of emails
     * @return this mailer context
     */
    public MailerContext to(String... recipients) {
        to = canonicalRecipients(null, recipients);
        return this;
    }

    public List<InternetAddress> to() {
        return to.isEmpty() ? mailerConfig().to() : to;
    }

    public MailerContext cc(String... recipients) {
        cc = canonicalRecipients(null, recipients);
        return this;
    }

    public List<InternetAddress> cc() {
        return cc.isEmpty() ? mailerConfig().ccList() : cc;
    }

    public MailerContext bcc(String... recipients) {
        bcc = canonicalRecipients(null, recipients);
        return this;
    }

    public List<InternetAddress> bcc() {
        return bcc.isEmpty() ? mailerConfig().bccList() : bcc;
    }

    public MailerContext addTo(String... recipients) {
        canonicalRecipients(to, recipients);
        return this;
    }

    public MailerContext addCc(String... recipients) {
        canonicalRecipients(cc, recipients);
        return this;
    }

    public MailerContext addBcc(String... recipients) {
        canonicalRecipients(bcc, recipients);
        return this;
    }

    public MimeMessage createMessage() throws Exception {
        Session session = mailerConfig().session();
        MimeMessage msg = new MimeMessage(session);

        msg.setFrom(from());
        msg.setSubject(subject());
        msg.setSentDate(new Date());

        msg.setRecipients(Message.RecipientType.TO, list2Array(to()));
        msg.setRecipients(Message.RecipientType.CC, list2Array(cc()));
        msg.setRecipients(Message.RecipientType.BCC, list2Array(bcc()));

        ViewManager vm = Act.viewManager();
        Template t = vm.load(this);
        String content = t.render(this);
        if (attachments.isEmpty()) {
            msg.setContent(content, accept().toContentType());
        } else {
            Multipart mp = new MimeMultipart();
            MimeBodyPart bp = new MimeBodyPart();
            mp.addBodyPart(bp);
            bp.setContent(content, accept().toContentType());
            for (ISObject sobj : attachments) {
                MimeBodyPart attachment = new MimeBodyPart();
                attachment.attachFile(sobj.asFile(), sobj.getAttribute(ISObject.ATTR_CONTENT_TYPE), "utf-8");
                mp.addBodyPart(attachment);
            }
            msg.setContent(mp);
        }
        return msg;
    }

    private static InternetAddress[] list2Array(List<InternetAddress> list) {
        int len = list.size();
        InternetAddress[] array = new InternetAddress[len];
        return list.toArray(array);
    }

    private static final String SEP = "[;:,]+";

    public static List<InternetAddress> canonicalRecipients(List<InternetAddress> l, String... recipients) {
        if (null == l) l = C.newList();
        if (recipients.length == 0) return l;
        String s = S.join(",", recipients).replaceAll(SEP, ",");
        try {
            InternetAddress[] aa = InternetAddress.parse(s);
            l.addAll(C.listOf(aa));
            return l;
        } catch (AddressException e) {
            throw E.unexpected(e);
        }
    }

}