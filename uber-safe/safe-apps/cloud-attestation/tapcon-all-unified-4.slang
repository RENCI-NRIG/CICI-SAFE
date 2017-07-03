defenv VMLevel :- "1".
defenv IaaS() :- 'hBZy2DLtG7qF7CxdZNWusPKdDz6LbA3lBDFIBDGbHTo'.

defcon makeMyIdentitySet(?CN) :-
  spec('Construct an identity set for self'),
  {
    principal($SelfKey) :- spec("identity set for $CN").
  }.

defpost postIdSet(?CN) :- [makeMyIdentitySet(?CN)].

defcon makeMySubjectSet() :-
  spec("Construct the subject set for self"),
  {
    link($Self).
    label("subject($Self)").
  }.

defpost postSubjectSet() :- [makeMySubjectSet()].

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
      HAuthNID: runGuestInstance(Instance, Image),
      HAuthNID: bindToAuthNID(Instance, AuthNID),
      attester(HAuthNID).

    attester(AuthNID) :- 
      runs(AuthNID, Image), 
      E: endorseAttesterImage(Image), 
      attesterImageEndorser(E).

    attester(H) :- trustedCloudProvider(H).
 
    label("tapcon-attestation-with-authN-policy").
  }.

definit attestationWithAuthNPolicySet().




defcon authenticationRuleSet() :-
  spec('Rules for authenticate a statement'),
  {
    runsGuestInstance(H, Instance, Image) :-
      NetAddr: runGuestInstance(Instance, Image),
      bindsToNetAddr(H, NetAddr).
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
  spec('Rules to grant program access privilege based on data owner's belief'),
  {
    programHasAccessPrivilege(Program, ObjectID) :-
      Owner := rootPrincipal(ObjectID),
      ownerAcceptsProgramProperty(Owner, Program, Property), 
      Owner: accessPrivilegeByProgramProperty(Property, ObjectID),
      validateBuildingChainOnImage(Program).    

    ownerAcceptsProgramProperty(Owner, Program, Property) :-
      Certifier: hasProgramProperty(Program, Property),
      Owner: trustCertifierOn(Certifier, Property).

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


// IaaS launches a cloud container
//
// A uniform guest attestation statement:
// runGuestInstance(GuestInstanceID, ?GuestImageHash, ?GuestLevel, ?StartTime)
//
defcon launchCloudContainer(?CCID, ?PolicyHash, ?TemplateID, ?TemplateArgs, ?NetAddr, ?ScidUnderHost, ?StartTime) :-
  spec('Launch a cloud container'),
  {
    runGuestInstance($CCID, $PolicyHash).
    instanceStartsAt($CCID, $StartTime).
    nameGuestInstance($CCID, $ScidUnderHost).
    bindNetworkAddr($ScidUnderHost, $NetAddr).
    guestConf($CCID, $PolicyHash, $TemplateID, $TemplateArgs).
    label("cloud-container/$CCID"). 
  }.

defpost postCloudContainer(?CCID, ?PolicyHash, ?TemplateID, ?TemplateArgs, ?NetAddr, ?Scid, ?StartTime) :-
  [launchCloudContainer(?CCID, ?PolicyHash, ?TemplateID, ?TemplateArgs, ?NetAddr, ?Scid, ?StartTime)].

// Cloud container launches a vm 
defcon launchVM(?InstanceID, ?ImgID, ?ConfigHash, ?NetAddr, ?ScidUnderHost, ?StartTime) :-
  ?ImgSet := label($IaaS, "vm-image/?ImgID"), 
  ?HostCloudContainerSet := label($Self, "subject($Self)"), 
  {
    link($ImgSet).
    link($HostCloudContainerSet).
    runGuestInstance($InstanceID, $ImgID). 
    instanceStartsAt($InstanceID, $StartTime).
    nameGuestInstance($InstanceID, $ScidUnderHost).
    bindNetworkAddr($ScidUnderHost, $NetAddr).
    guestConf($InstanceID, $ConfigHash).
    label("vm-instance/$InstanceID"). // update this link to the vm's subject set
  }.

defpost postVM(?InstanceID, ?ImgID, ?ConfigHash, ?NetAddr, ?Scid, ?StartTime) :-
  [launchVM(?InstanceID, ?ImgID, ?ConfigHash, ?NetAddr, ?Scid, ?StartTime)].

// IaaS builds vm images
// ImageID is a hash over source and config
defcon buildVMImg(?BuilderImgID, ?ImgID, ?GitHash, ?RevHash, ?Checksum, ?Timestamp) :-
  ?BuilderImgSet := label($IaaS, "vm-image/?BuilderImgID"),
  {
    link($BuilderImgSet).
    buildVMImg($BuilderImgID, $ImgID). 
    programIsImage("image").
    vmImgConfig($ImgID, $GitHash, $RevHash, $Checksum, $Timestamp).
    label("vm-image/$ImgID").
  }.

defpost postVMImg(?BuilderImgID, ?ImgID, ?GitHash, ?RevHash, ?Checksum, ?Timestamp) :-
  [buildVMImg(?BuilderImgID, ?ImgID, ?GitHash, ?RevHash, ?Checksum, ?Timestamp)].


// VM launches a Docker container
defcon launchContainer(?ContainerID, ?VMImgID, ?ConfigHash, ?NetAddr, ?ScidUnderHost, ?StartTime) :- 
  ?VMImgSet := label($IaaS, "vm-image/?VMImgID"),
  ?HostVMSet := label($Self, "subject($Self)"),
  {
    link($VMImgSet).
    link($HostVMSet).
    runGuestInstance($ContainerID, $VMImgID). 
    instanceStartsAt($ContainerID, $StartTime).
    nameGuestInstance($ContainerID, $ScidUnderHost).
    bindNetworkAddr($ScidUnderHost, $NetAddr).
    guestConf($ContainerID, $ConfigHash).
    label("container/$ContainerID").   // update this link to the container's subject set
  }.

defpost postContainer(?ContainerID, ?VMImgID, ?ConfigHash, ?NetAddr, ?ScidUnderHost, ?StartTime) :-
  [launchContainer(?ContainerID, ?VMImgID, ?ConfigHash, ?NetAddr, ?ScidUnderHost, ?StartTime)].

// Docker container launches a Spark app
defcon launchSparkApp(?AppID, ?AppHash, ?ConfigHash, ?NetAddr, ?ScidUnderHost, ?StartTime) :-
  ?DockerContainerSet := label($Self, "subject($Self)"),
  {
    link($DockerContainerSet).
    runGuestInstance($AppID, $AppHash).
    instanceStartsAt($AppID, $StartTime).
    nameGuestInstance($AppID, $ScidUnderHost).
    bindNetworkAddr($ScidUnderHost, $NetAddr).
    guestConf($AppID, $ConfigHash).
    label("spark-app/$AppID").         // update this link to the app's subject set
  }.

defpost postSparkApp(?AppID, ?AppHash, ?ConfigHash, ?StartTime) :-
  [launchSparkApp(?AppID, ?AppHash, ?ConfigHash, ?StartTime)].

defguard sparkAppAccessesObject(?AppID, ?ObjectID) :-
  ?DataOwner := rootPrincipal(?ObjectID),
  ?OwnerTrustWallet := label(?DataOwner, "trust wallet"),
  ?ACLSet := label(?DataOwner, "acl/?ObjectID"),
  ?AttestationSet := label(?AppID, "subject(?AppID)"),
  ?PolicySet := label("tapcon/anchor-set"),
  {
    link($OwnerTrustWallet).
    link($ACLSet).
    link($AttestationSet).
    link($PolicySet).
    approveAccess($AppID, $ObjectID)?
  }.

defcon anchorSet() :- 
  ?AttestationPolicy := label("tapcon-attestation-policy"),
  ?ProgramPrivilegePolicy := label("program-access-priv-policy"),
  ?AttesterImageEndorsingPolicy := label("attester-image-endorsing-policy"),
  ?ImageBuildingChainPolicy := label("image-building-policy"), 
  ?DeploymentPolicy := label("deployment-policy"),
  {
    trustedCloudProvider($IaaS).
    link($AttestationPolicy).
    link($ProgramPrivilegePolicy).
    link($AttesterImageEndorsingPolicy).
    link($ImageBuildingChainPolicy).
    link($DeploymentPolicy).
    label("tapcon/anchor-set").
  }.

definit anchorSet().
