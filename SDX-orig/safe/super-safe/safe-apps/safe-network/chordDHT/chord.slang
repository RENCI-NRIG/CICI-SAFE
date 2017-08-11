(*at CA*)

defcon addSecret(?NI,?Secret):-
  {
    secret($NI,$Secret).
    label("add shared secret").
  }.

defcon nodeIDCert(?Nid,?NI,?Key):-
  {
    $NI: requestCert($Key,$Self).
    secret($NI,$Secret).
    f_generateID($Nid,$Secret).
    label("Cert/$Self").
  }.

defpost issueCert(?Nid,?NI,?Key) :- [nodeIDCert(?Nid,?NI,?Key)].

defguard requestCert(?NI,?Key) :-
  ?RequestCertPolicy=label("request cert policy"),
  ?IssueCertPolicy=label("issue cert policy"),
  {
    link($RequestCertPolicy).
    link($IssueCertPolicy).
    link($BearerRef).
    nodeIDCert(?Nid,$NI,$Key)?
  }.

(*at node NI*)

defcon requestCert(?Key,?CA) :-
  {
    publicKey($Key).
    startNetwork().
    myCA($CA).
    label("request cert policy").
  }.

defpost postRequestCert(?Key, ?CA) :- [requestCert(?Key,?CA)].

defcon nodeIDPolicy():-
  {
    nodeID(?Nid):-
      ?CA: nodeIDCert(?Nid,$Self,?Key).
  }.

defcon announceCA(?CA):-
  {
    myCA($CA).
    label("announceCA").
  }.

defcon announcePK(?Key) :-
  {
    publicKey($Key).
    label("announe public key").
  }.

(*at landmark node LI*)

defcon acceptJoinPolicy() :-
  {
    acceptJoinRequest(?NI,?CA):-
      ?CA: nodeIDCert(?Nid,?NI,?Key),
      myCA(?CA).
    label("accept join request policy").
  }.

defguard acceptJoinRequest(?NI,?CA):-
  ?AcceptJoinPolicy=label("accept join request policy"),
  ?CACertSet=label("Cert/$CA"),
  {
    link($CACertSet).
    link($AcceptJoinPolicy).
    acceptJoinRequest($NI)?
  }.
