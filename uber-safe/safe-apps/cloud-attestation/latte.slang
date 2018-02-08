defenv IaaS() :- '152.3.145.38:444'.
//defenv Self() :- '152.3.136.26:446'.

//defenv Selfie() :-
//  spec('Loading principal keypair'),
//  principal('/home/qiang/Desktop/safe-new-version-benchmark/safe/safe-server/src/main/resources/multi-principal-keys/key_p8034.pem').

defcon addTokenToSubjectSet(?Token) :-
  spec("Add a token to a subject set. Invoke after the user gets a delegation"),
  {
    link($Token).
    label("subject($Self)").
  }.

defpost updateSubjectSet(?Token) :- [addTokenToSubjectSet(?Token)]. 

defcon addMembershipToSubjectSet(?MasterAuthNID) :-
  spec("Add cluster membership endorsed by a master to a subject set. Invoke after a worker gets a membership"),
  ?MemberSet := label(?MasterAuthNID, "workers/$Self"),
  {
    link($MemberSet).
    label("subject($Self)").
  }.

defpost updateWorkerSet(?MasterAuthNID) :- 
  [addMembershipToSubjectSet(?MasterAuthNID)]. 

defcon makeAttesterImage(?Image, ?Config) :-
  spec("Endorse an attester image"),
  {
    endorseAttesterImage($Image, $Config). 
    label("vm-image/$Image").
  }.

defpost postAttesterImage(?Image, ?Config) :- [makeAttesterImage(?Image, ?Config)]. 


defcon certifyImage(?ImgId, ?ConfigId, ?Property) :-
  spec("A third party certifies a property of an image"),
  {
    hasProgramProperty($ImgId, $ConfigId, $Property).
    label("vm-image/$ImgId").
  }.

defpost postImageProperty(?ImgId, ?ConfigId, ?Property) :- [certifyImage(?ImgId, ?ConfigId, ?Property)].


// Data owner: set up object ACLs based on image properties
defcon addObjectAcl(?ObjectId, ?Property) :-
  spec("A data owner adds an ACL to her object based on an image property"),
  {
     accessPrivilegeByProgramProperty($Property, $ObjectId).
     label("acl/$ObjectId").
  }.

defpost postObjectAcl(?ObjectId, ?Property) :- [addObjectAcl(?ObjectId, ?Property)].



// A VM image is identified by a hash over 
// ?Git, ?Revision, ?Checksum, ?Timestamp 
//
//  [vmImgWhiteList(?Git, ?Revision, ?Checksum, ?Timestamp)].

// Now Docker container images and vm images share the same ID space
// we might need a separate space for docker container images


defcon attestationPolicySet() :-
  spec('Rules to validate an attestation chain'),
  {
    approveAccess(Instance, ObjectID) :-
      runs(Instance, Program),
      programHasAccessPrivilege(Program, ObjectID). 
 
    runs(Instance, Image) :-
      runsGuestInstance(H, Instance, Image),
      attester(H).

    attester(Instance) :- 
      runs(Instance, Image), 
      E: endorseAttesterImage(Image), 
      attesterImageEndorser(E).

    attester(H) :- trustedCloudProvider(H).
 
    label("tapcon-attestation-policy").
  }.

definit attestationPolicySet().

defcon attestationWithAuthNPolicySet() :-
  spec('Rules for attestation and authentication'),
  {
    approveAccessFromWorker(WorkerIP, WorkerPort, ObjectID) :-
      runs(WorkerIP, WorkerPort, WorkerProgram, Config),
      programHasAccessPrivilege(WorkerProgram, Config, ObjectID),
      MasterAuthNID: clusterMember(WorkerIP, WorkerPort, WorkerID),
      MasterIP := ipFromNetworkID(MasterAuthNID),
      MasterPort := portFromNetworkID(MasterAuthNID),
      runs(MasterIP, MasterPort, MasterProgram, Config),
      programHasAccessPrivilege(MasterProgram, Config, ObjectID).

    approveAccess(AppIP, AppPort, ObjectID) :-
      runs(AppIP, AppPort, Program, Conf),
      programHasAccessPrivilege(Program, Conf, ObjectID). 
 
    runs(IP, Port, Image, Conf) :-
      HAuthNID: bindToAuthNID(GAuthNID, SuperIP, SuperPort, Instance),
      IP <: SuperIP,
      Port <: SuperPort,
      HAuthNID: runGuestInstance(Instance, Image),
      HAuthNID: guestConf(Instance, Conf),
      \+(delegatedRange(GAuthNID, IP, Port)),
      \+(poison(HAuthNID, Instance)),
      \+(vulnerableImage(Image)),
      attester(HAuthNID).

    delegatedRange(AuthNID, IP, Port) :- 
      AuthNID: bindToAuthNID(GAuthNID, SuperIP, SuperPort, Instance),
      IP <: SuperIP,
      Port <: SuperPort.

    poison(HAuthNID, Instance) :- HAuthNID: compromised(Instance).

    vulnerableImage(Image) :- trustedCloudProvider(E), E: vulnerable(Image). 

    //  \+(HAuthNID: compromised(Instance)),
    //  attester(HAuthNID).

    attester(AuthNID) :- 
      InstanceIP := ipFromNetworkID(AuthNID),
      InstancePort := portFromNetworkID(AuthNID),
      runs(InstanceIP, InstancePort, Image, Conf), 
      E: endorseAttesterImage(Image, Conf), 
      attesterImageEndorser(E).

    attesterImageEndorser(E) :- trustedCloudProvider(E).

    attester(H) :- trustedCloudProvider(H).

    programHasProperty(AppIP, AppPort, Property) :-
      runs(AppIP, AppPort, Program, Config),
      Certifier: hasProgramProperty(Program, Config, Property),
      trustedCertifier(Certifier).
 
    label("tapcon-attestation-with-authN-policy").
  }.

definit attestationWithAuthNPolicySet().


defcon authenticationRuleSet() :-
  spec('Rules for authenticate a statement'),
  {
    runsGuestInstance(H, Instance, Image) :-
      H: runGuestInstance(Instance, Image).
      //NetAddr: runGuestInstance(Instance, Image),
      //bindsToNetAddr(H, NetAddr).
      //speaksFor(NetAddr, H).

    //speaksFor(NetAddr, Instance) :-
    //  H: bindToNetAddr(Instance, Addr),
    //  attester(H).  

     //bindsToNetAddr(Instance, NetAddr) :-
     //  HNetAddr: bindToNetAddr(Instance, NetAddr),
     //  bindsToNetAddr(H, HNetAddr).

    label("tapcon-authentication").
  }.

definit authenticationRuleSet().


defcon programPrivilegePolicySet() :-
  spec("Rules to grant program access privilege based on data owner's belief"),
  {
    programHasAccessPrivilege(Program, Conf, ObjectID) :-
      Owner := rootPrincipal(ObjectID),
      ownerAcceptsProgramProperty(Owner, Program, Conf, Property), 
      Owner: accessPrivilegeByProgramProperty(Property, ObjectID).
      //validateBuildingChainOnImage(Program).    

    ownerAcceptsProgramProperty(Owner, Program, Conf, Property) :-
      Certifier: hasProgramProperty(Program, Conf, Property),
      trustedCertifier(Certifier).
      //Owner: trustCertifierOn(Certifier, Property).

    trustedCertifier(C) :- trustedCloudProvider(C).

    label('program-access-priv-policy').
  }.

definit programPrivilegePolicySet().

defcon attesterImagePolicySet() :-
  spec('Rules to check endorsers of an attester image'),
  {
    attesterImageEndorser(Endorser) :- 
      attesterImageEndorser(Endorser, _).

    attesterImageEndorser(P, Delegatable) :- 
      Delegator: delegateAttesterImageEndorsingCap(P, Delegatable),
      attesterImageEndorser(Delegator, true).

    label('attester-image-endorsing-policy').
  }.

definit trustedHostImagePolicySet().

defcon imageBuildingChainPolicySet() :-
  spec('Policies to validate the building chain of an image'),
  {

    validateBuildingChainOnImage(Program) :- 
      programIsImage(Program, ProgramType), 
      acceptImageBuildingChain(ProgramHash, ProgramType).

    acceptImageBuildingChain(Program, ProgramType) :-
      ?ProgramType = "non_image".

    acceptImageBuildingChain(Program, ?ProgramType) :-
      acceptImageBuildingChain(Program).

    acceptImageBuildingChain(Image) :-
      Endorser: tapconImage(Image),
      imageEndorser(Endorser).

    acceptImageBuildingChain(Image) :-
      Endorser: buildVMImg(BuilderImage, Image),
      imageEndorser(Endorser),
      acceptImageBuildingChain(BuilderImage).

    imageEndorser(Endorser) :- trustedCloudProvider(Endorser).

    label('image-building-policy').
  }.

definit imageBuildingChainPolicySet().

//
// Image property checking
//

defcon imageSafetyPolicySet() :-
  spec('Rules to check whether an image has an endorsed safety property'),
  {
    hasSafetyProperty(ImgId, ConfigId, Property) :-
      $IaaS: hasProgramProperty(ImgId, ConfigId, Property).

    label("tapcon-image-safety-policy").
  }.

definit imageSafetyPolicySet().

defcon deploymentRuleSet() :-
  spec('Rules specific to tapcon deployment'),
  {
    
    hostContainsGuest(Host, Guest) :-
      hostEndorsesGuest(Guest, Host, GuestProgram, GuestScid, GuestNetAddr),
      hostEndorsesGuest(Host, HHID, HostProgram, HostScid, HostNetAddr),
      GuestNetAddr <: HostNetAddr,
      AuthorityOfGuest := rootPrincipal(GuestScid),
      AuthorityOfGuest = Host.

    hostEndorsesGuest(GuestID, Host, GuestProgram, GuestScid, GuestNetAddr) :-
      Host: runGuestInstance(GuestID, GuestProgram),
      Host: nameGuestInstance(GuestID, GuestScid),
      Host: bindNetworkAddr(GuestScid, GuestNetAddr).
  
    label("deployment-policy").
  }.

definit deploymentRuleSet().

defcon makeTapconVMImage(?ImageHash) :-
  spec("IaaS announces a Tapcon VM image"),
  {
    tapconImage($ImageHash).
    label("tapcon-images/$ImageHash").
  }. 

defpost postTapconVMImage(?ImageHash) :- 
  [makeTapconVMImage(?ImageHash)].


defcon launchInstance(?InstanceID, ?Image, ?ProgramType, ?GuestAuthNID, ?GuestConfig) :-
  ?ImgSet := label($IaaS, "vm-image/?Image"), 
  ?HostSet := label($Self, "subject($Self)"), 
  ?GuestIP := ipFromNetworkID(?GuestAuthNID),
  ?GuestPort := portFromNetworkID(?GuestAuthNID),
  {
    link($ImgSet).
    link($HostSet). 
    runGuestInstance($InstanceID, $Image).
    bindToAuthNID($GuestAuthNID, $GuestIP, $GuestPort, $InstanceID).
    programIsImage($Image, $ProgramType).
    guestConf($InstanceID, $GuestConfig).
    label("guest-instance/$GuestAuthNID").  // update this link to the guest's subject set 
  }.

defpost postInstanceSet(?InstanceID, ?Image, ?ProgramType, ?GuestAuthNID, ?GuestConfig) :-
  [launchInstance(?InstanceID, ?Image, ?ProgramType, ?GuestAuthNID, ?GuestConfig)].

defcon tearDownInstance(?InstanceID, ?Image, ?ProgramType, ?GuestAuthNID, ?GuestConfig) :-
  ?ImgSet := label($IaaS, "vm-image/?Image"), 
  ?HostSet := label($Self, "subject($Self)"), 
  ?GuestIP := ipFromNetworkID(?GuestAuthNID),
  ?GuestPort := portFromNetworkID(?GuestAuthNID),
  {
    link($ImgSet)~
    link($HostSet)~ 
    runGuestInstance($InstanceID, $Image)~
    bindToAuthNID($GuestAuthNID, $GuestIP, $GuestPort, $InstanceID)~
    programIsImage($Image, $ProgramType)~
    guestConf($InstanceID, $GuestConfig)~
    label("guest-instance/$GuestAuthNID"). 
  }.

defpost retractInstanceSet(?InstanceID, ?Image, ?ProgramType, ?GuestAuthNID, ?GuestConfig) :-
  [tearDownInstance(?InstanceID, ?Image, ?ProgramType, ?GuestAuthNID, ?GuestConfig)].

defcon launchWorker(?WorkerID, ?WorkerAuthNID, ?WorkerConfig) :-
  ?MasterSet := label($Self, "subject($Self)"), 
  ?WorkerIP := ipFromNetworkID(?WorkerAuthNID),
  ?WorkerPort := portFromNetworkID(?WorkerAuthNID),
  {
    link($MasterSet). 
    clusterMember($WorkerIP, $WorkerPort, $WorkerID).
    workerConf($WorkerID, $WorkerConfig).
    label("workers/$WorkerAuthNID").  // update this link to the worker's subject set 
  }.

defpost postWorkerSet(?WorkerID, ?WorkerAuthNID, ?WorkerConfig) :-
  [launchWorker(?WorkerID, ?WorkerAuthNID, ?WorkerConfig)].

defcon tearDownWorker(?WorkerID, ?WorkerAuthNID, ?WorkerConfig) :-
  ?MasterSet := label($Self, "subject($Self)"), 
  ?WorkerIP := ipFromNetworkID(?WorkerAuthNID),
  ?WorkerPort := portFromNetworkID(?WorkerAuthNID),
  {
    link($MasterSet)~ 
    clusterMember($WorkerIP, $WorkerPort, $WorkerID)~
    workerConf($WorkerID, $WorkerConfig)~
    label("workers/$WorkerAuthNID"). 
  }.

defpost retractWorkerSet(?WorkerID, ?WorkerAuthNID, ?WorkerConfig) :-
  [tearDownWorker(?WorkerID, ?WorkerAuthNID, ?WorkerConfig)].

defcon poisonInstance(?InstanceID, ?GuestAuthNID) :-
  {
    compromised($InstanceID).
    label("guest-instance/$GuestAuthNID"). 
  }.

defpost postInstancePoison(?InstanceID, ?GuestAuthNID) :-
  [poisonInstance(?InstanceID, ?GuestAuthNID)].

defcon poisonImage(?Image) :-
  {
    vulnerable($Image).
    label("vm-image/$Image").
  }.

defpost postImagePoison(?Image) :-
  [poisonImage(?Image)].

defguard workerAccessesObject(?WorkerID, ?ObjectID) :-
  ?DataOwner := rootPrincipal(?ObjectID),
  //?OwnerTrustWallet := label(?DataOwner, "trust wallet"),
  ?ACLSet := label(?DataOwner, "acl/?ObjectID"),
  ?WorkerSet := label(?WorkerID, "subject(?WorkerID)"),
  ?PolicySet := label("tapcon/anchor-set"),
  ?WorkerIP := ipFromNetworkID(?WorkerID),
  ?WorkerPort := portFromNetworkID(?WorkerID),
  {
    //link($OwnerTrustWallet).
    link($BearerRef).
    link($ACLSet).
    link($WorkerSet).
    link($PolicySet).
    approveAccessFromWorker($WorkerIP, $WorkerPort, $ObjectID)?
    //runs($AppID, ?Image)?
  }.


defguard appAccessesObject(?AppID, ?ObjectID) :-
  ?DataOwner := rootPrincipal(?ObjectID),
  //?OwnerTrustWallet := label(?DataOwner, "trust wallet"),
  ?ACLSet := label(?DataOwner, "acl/?ObjectID"),
  ?AppSet := label(?AppID, "subject(?AppID)"),
  ?PolicySet := label("tapcon/anchor-set"),
  ?AppIP := ipFromNetworkID(?AppID),
  ?AppPort := portFromNetworkID(?AppID),
  {
    //link($OwnerTrustWallet).
    link($BearerRef).
    link($ACLSet).
    link($AppSet).
    link($PolicySet).
    approveAccess($AppIP, $AppPort, $ObjectID)?
    //runs($AppID, ?Image)?
  }.

defguard attestInstance(?AppID) :-
  ?AppSet := label(?AppID, "subject(?AppID)"),
  ?PolicySet := label("tapcon/anchor-set"),
  ?AppIP := ipFromNetworkID(?AppID),
  ?AppPort := portFromNetworkID(?AppID),
  {
    link($BearerRef).
    link($AppSet).
    link($PolicySet).
    runs($AppIP, $AppPort, ?Image, ?Conf)?
  }.

defguard attestAppProperty(?AppID, ?Property) :-
  ?AppSet := label(?AppID, "subject(?AppID)"),
  ?PolicySet := label("tapcon/anchor-set"),
  ?AppIP := ipFromNetworkID(?AppID),
  ?AppPort := portFromNetworkID(?AppID),
  {
    link($BearerRef).
    link($AppSet).
    link($PolicySet).
    programHasProperty($AppIP, $AppPort, $Property)?
  }.

defguard checkImgProperty(?ImgID, ?Config, ?Property) :-
  ?ImgSet := label($IaaS, "vm-image/?ImgID"),
  ?PolicySet := label("tapcon-image-safety-policy"),
  {
    link($ImgSet).
    link($PolicySet).
    hasSafetyProperty($ImgID, $Config, $Property)?
  }.

defcon anchorSet() :- 
  ?AttestationPolicy := label("tapcon-attestation-with-authN-policy"),
  ?ProgramPrivilegePolicy := label("program-access-priv-policy"),
  ?AttesterImageEndorsingPolicy := label("attester-image-endorsing-policy"),
  ?ImageBuildingChainPolicy := label("image-building-policy"), 
  //DeploymentPolicy := label("deployment-policy"),
  {
    trustedCloudProvider($IaaS).
    link($AttestationPolicy).
    link($ProgramPrivilegePolicy).
    link($AttesterImageEndorsingPolicy).
    link($ImageBuildingChainPolicy).
    //link($DeploymentPolicy).
    label("tapcon/anchor-set").
  }.

definit anchorSet().
