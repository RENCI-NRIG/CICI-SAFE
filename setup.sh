# User set variables.
SSH_KEY=~/.ssh/id_rsa
EXOGENI_SSL_PEM=~/.ssh/geni-rubenfa.pem

# User may potentially want to set these
CURRENT_DIR=`pwd`
CLIENT_SITES=("UFL (Gainesville, FL USA) XO Rack" "UNF (Jacksonville, FL) XO Rack")
NUM_CLIENT_SITES=${#CLIENT_SITES[@]}

for index in $(seq 1 4)
do
  CLIENT_CFG="config{
    slicename=\"$USER-c$index\"
    exogenipem=\"$EXOGENI_SSL_PEM\"
    sshkey=\"$SSH_KEY\"
    ipprefix=\"192.168.${index}0.1/24\"
    type=\"client\"
    serverurl=\"http://0.0.0.0:8888/\"
    exogenism=\"https://geni.renci.org:11443/orca/xmlrpc\"
    serversite=\"${CLIENT_SITES[$index % $NUM_CLIENT_SITES ]}\"
    routersite=\"UFL (Gainesville, FL USA) XO Rack\"
  }";
echo "${CLIENT_CFG}" > SDX-Simple/client-config/c$index.conf
done

CLIENT_SITES_APPENDED=""
CLIENT_SITES_APPENDED=""
LOOP_END=$((NUM_CLIENT_SITES-1))
for index in $(seq 0 ${LOOP_END})
do
  CLIENT_SITES_APPENDED=$CLIENT_SITES_APPENDED${CLIENT_SITES[$index]}
  if [ $index != $LOOP_END ]
  then
    CLIENT_SITES_APPENDED=$CLIENT_SITES_APPENDED:
  fi
done

SERVER_CONFIG="config{
  slicename=\"$USER-test\"
  type=\"server\"
  sshkey=\"$SSH_KEY\"
  exogenipem=\"$EXOGENI_SSL_PEM\"
  exogenism=\"https://geni.renci.org:11443/orca/xmlrpc\"
  ipprefix=\"192.168.128.1/20\"
  bro = true
  brobw = 100000000
  routernum=2
  bw = 2000000000
  serverurl=\"http://0.0.0.0:8888/\"
  scriptsdir=\"$CURRENT_DIR/SDX-Simple/SAFE_SDX/src/main/resources/scripts/\"
  resourcedir=\"$CURRENT_DIR/SDX-Simple/SAFE_SDX/src/main/resources/\"
  topodir=\"$CURRENT_DIR/SDX-Simple/topo/\"
  clientsites=\"$CLIENT_SITES_APPENDED\"
  controllersite=\"TAMU (College Station, TX, USA) XO Rack\"
  serversite=\"TAMU (College Station, TX, USA) XO Rack\"
  sitelist=[\"BBN/GPO (Boston, MA USA) XO Rack\",\"CIENA (Ottawa,  CA) XO Rack\",\"FIU (Miami, FL USA) XO Rack\",\"GWU (Washington DC,  USA) XO Rack\",\"OSF (Oakland, CA USA) XO Rack\",\"RENCI (Chapel Hill, NC USA) XO Rack\",\"SL (Chicago, IL USA) XO Rack\",\"TAMU (College Station, TX, USA) XO Rack\",\"UAF (Fairbanks, AK, USA) XO Rack\",\"UFL (Gainesville, FL USA) XO Rack\",\"UH (Houston, TX USA) XO Rack\",\"UMass (UMass Amherst, MA, USA) XO Rack\",\"UNF (Jacksonville, FL) XO Rack\",\"UvA (Amsterdam, The Netherlands) XO Rack\",\"WSU (Detroit, MI, USA) XO Rack\",\"WVN (UCS-B series rack in Morgantown, WV, USA)\"]
}";

echo "${SERVER_CONFIG}" > SDX-Simple/config/test.conf

### List of potential sites:
# BBN/GPO (Boston, MA USA) XO Rack
# CIENA (Ottawa,  CA) XO Rack
# FIU (Miami, FL USA) XO Rack
# GWU (Washington DC,  USA) XO Rack
# OSF (Oakland, CA USA) XO Rack
# RENCI (Chapel Hill, NC USA) XO Rack
# SL (Chicago, IL USA) XO Rack
# TAMU (College Station, TX, USA) XO Rack
# UAF (Fairbanks, AK, USA) XO Rack
# UFL (Gainesville, FL USA) XO Rack
# UH (Houston, TX USA) XO Rack
# UMass (UMass Amherst, MA, USA) XO Rack
# UNF (Jacksonville, FL) XO Rack
# UvA (Amsterdam, The Netherlands) XO Rack
# WSU (Detroit, MI, USA) XO Rack
# WVN (UCS-B series rack in Morgantown, WV, USA)"]

