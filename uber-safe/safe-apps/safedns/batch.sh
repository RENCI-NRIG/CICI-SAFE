# Identity set for dot
runMain safe.safelang.Repl -f userInit.slang -a keys/dot.pem,.

# Identity set for edu
runMain safe.safelang.Repl -f userInit.slang -a keys/edu.pem,edu

# Identity set for com
runMain safe.safelang.Repl -f userInit.slang -a keys/com.pem,com

# Identity set for org
runMain safe.safelang.Repl -f userInit.slang -a keys/org.pem,org

# Identity set for google.com
runMain safe.safelang.Repl -f userInit.slang -a keys/google.com.pem,google

# Identity set for duke.edu
runMain safe.safelang.Repl -f userInit.slang -a keys/duke.edu.pem,duke

# Identity set for unc.edu
runMain safe.safelang.Repl -f userInit.slang -a keys/unc.edu.pem,unc

# Identity set for cs.duke.edu
runMain safe.safelang.Repl -f userInit.slang -a keys/cs.duke.edu.pem,cs

# IssueSRN . -> edu
runMain safe.safelang.Repl -f issueSRN.slang -a keys/dot.pem,u'DXml70z2pqI6D67S0BlkKLuHV0tHjZFlEhLc99Ln-zc',edu

# IssueSRN . -> com
runMain safe.safelang.Repl -f issueSRN.slang -a keys/dot.pem,u'YMcFEQy4j4fu25tF5DjZCHIHRHpES8MabircgGFm-iQ',com

# IssueSRN . -> org
runMain safe.safelang.Repl -f issueSRN.slang -a keys/dot.pem,u'nT4CKe-Ifu1YfkklAFH7ea2RJ0l1ibKq2wu6UyQUp70',org

# IssueSRN com -> google
runMain safe.safelang.Repl -f issueSRN.slang -a keys/com.pem,u'tP-h619tr0O4qKq978PT0fagz9cGfyeVcM_ELjTa3sk',google

# IssueSRN edu -> duke
runMain safe.safelang.Repl -f issueSRN.slang -a keys/edu.pem,u'p8HiyByjRSbA9bM7zp0OKb0kap6slDVzrmNt3yMTE7w',duke

# IssueSRN edu -> unc
runMain safe.safelang.Repl -f issueSRN.slang -a keys/edu.pem,u'f3xek5k2RcvznzVCv9coTAbmFWUYBnFo3fefJJo1e4E',unc

# IssueSRN edu -> duke -> cs
runMain safe.safelang.Repl -f issueSRN.slang -a keys/duke.edu.pem,u'wyXGWmt5pxKwR6GwludNNYgehsh78rKyoFSUKg5E9Bk',cs

# IssueSRN com -> duke
runMain safe.safelang.Repl -f issueSRN.slang -a keys/com.pem,u'p8HiyByjRSbA9bM7zp0OKb0kap6slDVzrmNt3yMTE7w',duke


# Add 'A Record' for cs.duke.edu
#runMain safe.safelang.Repl -f aRecord.slang -a keys/cs.duke.edu.pem,'aRecord',dn"cs.duke.edu",ipv4"172.168.0.1"
runMain safe.safelang.Repl -f aRecord.slang -a keys/cs.duke.edu.pem,'aRecord',dn"cs.duke.edu",'172.168.0.1'
