//defenv VMLevel :- "1".
defenv IaaS() :- '152.3.145.38:447'.
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

defcon makeAttesterImage(?Image) :-
  spec("Endorse an attester image"),
  {
    endorseAttesterImage($Image). 
    label("vm-image/$Image").
  }.

defpost postAttesterImage(?Image) :- [makeAttesterImage(?Image)]. 


defcon certifyImage(?ImgId, ?Property) :-
  spec("A third party certifies a property of an image"),
  {
    hasProgramProperty($ImgId, $Property).
    label("vm-image/$ImgId").
  }.

defpost postImageProperty(?ImgId, ?Property) :- [certifyImage(?ImgId, ?Property)].


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
    approveAccess(AuthNID, ObjectID) :-
      runs(AuthNID, Program),
      programHasAccessPrivilege(Program, ObjectID). 
 
    runs(AuthNID, Image) :-
      HAuthNID: bindToAuthNID(AuthNID, Instance),
      HAuthNID: runGuestInstance(Instance, Image),
      attester(HAuthNID).

    attester(AuthNID) :- 
      runs(AuthNID, Image), 
      E: endorseAttesterImage(Image), 
      attesterImageEndorser(E).

    attesterImageEndorser(E) :- trustedCloudProvider(E).

    attester(H) :- trustedCloudProvider(H).

    programHasProperty(AuthNID, Property) :-
      runs(AuthNID, Program),
      Certifier: hasProgramProperty(Program, Property),
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
    programHasAccessPrivilege(Program, ObjectID) :-
      Owner := rootPrincipal(ObjectID),
      ownerAcceptsProgramProperty(Owner, Program, Property), 
      Owner: accessPrivilegeByProgramProperty(Property, ObjectID).
      //validateBuildingChainOnImage(Program).    

    ownerAcceptsProgramProperty(Owner, Program, Property) :-
      Certifier: hasProgramProperty(Program, Property),
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

defcon deploymentRuleSet() :-
  spec('Rules specific to tapcon deployment'),
  {
    
    hostContainsGuest(Host, Guest) :-
      hostEndorsesGuest(Guest, Host, GuestProgram, GuestScid, GuestNetAddr),
      hostEndorsesGuest(Host, HHID, HostProgram, HostScid, HostNetAddr),
      GuestNetAddr <: HostNetAddr,
      AuthorityOfGuest := rootPrincipal(GuestScid),
      AuthorityOfGuest = Host,

    hostEndorsesGuest(GuestID, Host, GuestProgram, GuestScid, GuestNetAddr),
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


defcon launchInstance(?InstanceID, ?Image, ?ProgramType, ?GuestAuthNID) :-
  ?ImgSet := label($IaaS, "vm-image/?Image"), 
  ?HostSet := label($Self, "subject($Self)"), 
  {
    link($ImgSet).
    link($HostSet). 
    runGuestInstance($InstanceID, $Image).
    bindToAuthNID($GuestAuthNID, $InstanceID).
    programIsImage($Image, $ProgramType).
    label("guest-instance/$GuestAuthNID").  // update this link to the guest's subject set 
  }.

defpost postInstanceSet(?InstanceID, ?Image, ?ProgramType, ?GuestAuthNID) :-
  [launchInstance(?InstanceID, ?Image, ?ProgramType, ?GuestAuthNID)].


defguard appAccessesObject(?AppID, ?ObjectID) :-
  ?DataOwner := rootPrincipal(?ObjectID),
  //?OwnerTrustWallet := label(?DataOwner, "trust wallet"),
  ?ACLSet := label(?DataOwner, "acl/?ObjectID"),
  ?AppSet := label(?AppID, "subject(?AppID)"),
  ?PolicySet := label("tapcon/anchor-set"),
  {
    //link($OwnerTrustWallet).
    link($ACLSet).
    link($AppSet).
    link($PolicySet).
    approveAccess($AppID, $ObjectID)?
    //runs($AppID, ?Image)?
  }.

defguard attestInstance(?AppID) :-
  ?AppSet := label(?AppID, "subject(?AppID)"),
  ?PolicySet := label("tapcon/anchor-set"),
  {
    link($AppSet).
    link($PolicySet).
    runs($AppID, ?Image)?
  }.

defguard attestAppProperty(?AppID, ?Property) :-
  ?AppSet := label(?AppID, "subject(?AppID)"),
  ?PolicySet := label("tapcon/anchor-set"),
  {
    link($AppSet).
    link($PolicySet).
    programHasProperty($AppID, $Property)?
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
