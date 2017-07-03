# Identity set for geniRoot
runMain safe.safelang.Repl -f /home/vamsi/Code/repo/wowmsi/safe/safe-apps/geni/user_init.slang -a /home/vamsi/Code/repo/wowmsi/safe/safe-apps/geni/geniRoot_keyPair.pem,geniRoot

# Identity set for idp
runMain safe.safelang.Repl -f /home/vamsi/Code/repo/wowmsi/safe/safe-apps/geni/coordinators_init.slang -a /home/vamsi/Code/repo/wowmsi/safe/safe-apps/geni/idp_keyPair.pem,idp

# Identity set for pa
runMain safe.safelang.Repl -f /home/vamsi/Code/repo/wowmsi/safe/safe-apps/geni/coordinators_init.slang -a /home/vamsi/Code/repo/wowmsi/safe/safe-apps/geni/pa_keyPair.pem,pa

# Identity set for sa
runMain safe.safelang.Repl -f /home/vamsi/Code/repo/wowmsi/safe/safe-apps/geni/coordinators_init.slang -a /home/vamsi/Code/repo/wowmsi/safe/safe-apps/geni/sa_keyPair.pem,sa

# Identity set for cp
runMain safe.safelang.Repl -f /home/vamsi/Code/repo/wowmsi/safe/safe-apps/geni/coordinators_init.slang -a /home/vamsi/Code/repo/wowmsi/safe/safe-apps/geni/cp_keyPair.pem,cp

# Identity set for pi1
runMain safe.safelang.Repl -f /home/vamsi/Code/repo/wowmsi/safe/safe-apps/geni/user_init.slang -a /home/vamsi/Code/repo/wowmsi/safe/safe-apps/geni/pi1_keyPair.pem,pi1

# Identity set for user1
runMain safe.safelang.Repl -f /home/vamsi/Code/repo/wowmsi/safe/safe-apps/geni/user_init.slang -a /home/vamsi/Code/repo/wowmsi/safe/safe-apps/geni/user1_keyPair.pem,user1

# Identity set for user2
runMain safe.safelang.Repl -f /home/vamsi/Code/repo/wowmsi/safe/safe-apps/geni/user_init.slang -a /home/vamsi/Code/repo/wowmsi/safe/safe-apps/geni/user2_keyPair.pem,user2

# Geni endorse coordinators
runMain safe.safelang.Repl -f /home/vamsi/Code/repo/wowmsi/safe/safe-apps/geni/geniRoot.slang -a /home/vamsi/Code/repo/wowmsi/safe/safe-apps/geni/geniRoot_keyPair.pem,As1r56WiIHkxzci8valHOGyDYNXD4GPWbmMqbHDXwp8,q-O1jJKpE7ZMVyf-3nKhYgEOjGvE16UJtA6h45DxgKE,825QDOgLcetdp4qOvGZRc9nTqSuQPQdRmEg5j-nXG44,ay1HJXe5v38PG0uGOgU10G2Pstn3wcE9-7mcXb5JmxQ
#idp, pa, sa, cp

# IdP endorse PI and User
runMain safe.safelang.Repl -f /home/vamsi/Code/repo/wowmsi/safe/safe-apps/geni/idp.slang -a /home/vamsi/Code/repo/wowmsi/safe/safe-apps/geni/idp_keyPair.pem,8Hlq_5XH7C25YCpPklHk3XNn4B-asWRqiAdPJdyXFVY,1RF6d7jpvAVVsByofu7XLx4-O9Qhbd5eXLGhFSGQTps
# pi1, user1

# PA publish membership set and create project 
runMain safe.safelang.Repl -f /home/vamsi/Code/repo/wowmsi/safe/safe-apps/geni/pa.slang -a /home/vamsi/Code/repo/wowmsi/safe/safe-apps/geni/pa_keyPair.pem,8Hlq_5XH7C25YCpPklHk3XNn4B-asWRqiAdPJdyXFVY
# pi1

# SA publish slice control policies and create slice
runMain safe.safelang.Repl -f /home/vamsi/Code/repo/wowmsi/safe/safe-apps/geni/sa.slang -a /home/vamsi/Code/repo/wowmsi/safe/safe-apps/geni/sa_keyPair.pem,8Hlq_5XH7C25YCpPklHk3XNn4B-asWRqiAdPJdyXFVY,1RF6d7jpvAVVsByofu7XLx4-O9Qhbd5eXLGhFSGQTps
# pi1, user1

# CP publish aggregate policy set
runMain safe.safelang.Repl -f /home/vamsi/Code/repo/wowmsi/safe/safe-apps/geni/cpPost.slang -a /home/vamsi/Code/repo/wowmsi/safe/safe-apps/geni/cp_keyPair.pem,ay1HJXe5v38PG0uGOgU10G2Pstn3wcE9-7mcXb5JmxQ

# CP guard check
#runMain safe.safelang.Repl -f /home/vamsi/Code/repo/wowmsi/safe/safe-apps/geni/cpGuard.slang -a /home/vamsi/Code/repo/wowmsi/safe/safe-apps/geni/cp_keyPair.pem,ay1HJXe5v38PG0uGOgU10G2Pstn3wcE9-7mcXb5JmxQ
