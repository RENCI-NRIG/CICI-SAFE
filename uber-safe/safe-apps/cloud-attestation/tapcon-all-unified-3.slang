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

defcon tapconDataSharingPolicySet() :-
  spec('Tapcon data-sharing policies'), 
  {
    approveAccess(?InstanceID, ?ObjectID) :- 
      approveAccess(?InstanceID, ?ObjectID, _).
      
    approveAccess(?InstanceID, ?ObjectID, ?AccessForBundle) :-   
      trustedCloudProvider(?InstanceID).

    approveAccess(?InstanceID, ?ObjectID, ?AccessForBundle) :-   
      hostAttestsGuest(?InstanceID, ?Host, ?GuestProgramHash),
      approveAccess(?Host, ?ObjectID, ?AccessForHostBundle),
      ?SkipGuestProgramCheck = ?AccessForHostBundle,
      programHasAccessPrivilege(?GuestProgramHash, ?ObjectID, ?SkipGuestProgramCheck, ?AccessForBundle).

    approveAccessNoBundle(?InstanceID, ?ObjectID) :-   
      trustedCloudProvider(?InstanceID).

    approveAccessNoBundle(?InstanceID, ?ObjectID) :-   
      hostAttestsGuest(?InstanceID, ?Host, ?GuestProgramHash),
      approveAccessNoBundle(?Host, ?ObjectID),
      programHasAccessPrivilege(?GuestProgramHash, ?ObjectID, _). 
 
    label("tapcon-data-policy").
  }.

definit tapconDataSharingPolicySet().


defcon attestationPolicySet() :-
  spec('Policies to validate an attestation and to obtain an attestation chain'),
  {
    attestationChain(?InstanceID, []) :-
      trustedCloudProvider(?InstanceID).

    attestationChain(?InstanceID, [?GuestProgramHash | ?HostChain]) :-
      hostAttestsGuest(?InstanceID, ?Host, ?GuestProgramHash),
      attestationChain(?Host, ?HostChain).

    hostAttestsGuest(?InstanceID, ?Host, ?ProgramHash) :-
      hostEndorsesGuest(?InstanceID, ?Host, ?ProgramHash, ?Scid, ?NetAddr),
      hostContainsGuest(?Host, ?Scid, ?NetAddr).

    hostContainsGuest(?Host, ?GuestScid, ?GuestNetAddr) :-
      hostEndorsesGuest(?Host, ?HHID, ?HostProgramHash, ?HostScid, ?HostNetAddr),
      ?GuestNetAddr <: ?HostNetAddr,
      ?AuthorityOfGuest := rootPrincipal(?GuestScid),
      ?AuthorityOfGuest = ?Host,
      acceptAttester(?HostProgramHash).

    hostEndorsesGuest(?GuestID, ?Host, ?GuestProgramHash, ?GuestScid, ?GuestNetAddr),
      ?Host: runGuestInstance(?GuestID, ?GuestProgramHash),
      ?Host: nameGuestInstance(?GuestID, ?GuestScid),
      ?Host: bindNetworkAddr(?GuestScid, ?GuestNetAddr).
 
    label("tapcon-attestation-policy").
  }.

definit attestationValidationPolicySet().

defcon programPrivilegePolicySet() :-
  spec('Policies to grant program access privilege based on the owner's belief'),
  {
    programHasAccessPrivilege(?ProgramHash, ?ObjectID, ?SkipCheck, ?AccessForBundle) :-
      ?SkipCheck = true,
      ?AccessForBundle = true.
 
    programHasAccessPrivilege(?ProgramHash, ?ObjectID, false, ?AccessForBundle) :-
      programHasAccessPrivilege(?ProgramHash, ?ObjectID, ?AccessForBundle).  

    programHasAccessPrivilege(?ProgramHash, ?ObjectID, ?BundleAccess) :-
      ?Owner := rootPrincipal(?ObjectID),
      ownerAcceptsProgramProperty(?Owner, ?ProgramHash, ?Property), 
      ?Owner: accessPrivilegeByProgramProperty(?Property, ?ObjectID, ?BundleAccess),
      validateBuildingChainOnImage(?ProgramHash).    

    ownerAcceptsProgramProperty(?Owner, ?ProgramHash, ?Property) :-
      ?Certifier: hasProgramProperty(?ProgramHash, ?Property),
      ?Owner: trustCertifierOn(?Certifier, ?Property).

    label('program-access-privilege-policy').
  }.

definit programPrivilegePolicySet().

defcon trustedHostImagePolicySet() :-
  spec('Policies to check if an image can be used to instantiate a valid attester'),
  {
    acceptAttester(?HostProgramHash) :-
      ?Endorser: trustedHost(?HostProgramHash),
      trustedHostEndorser(?Endorser).

    trustedHostEndorser(?Endorser) :- 
      trustedHostEndorser(?Endorser, _).

    trustedHostEndorser(?P, ?Delegatable) :- 
      ?Delegator: delegateTrustedHostEndorsingCap(?P, ?Delegatable),
      trustedHostEndorser(?Delegator, true).

    label('trusted-host-image-policy').
  }.

definit trustedHostImagePolicySet().

defcon imageBuildingChainPolicySet() :-
  spec('Policies to validate the building chain of an image'),
  {

    validateBuildingChainOnImage(?ProgramHash) :- 
      programIsImage(?ProgramHash, ?ProgramType). 
      acceptImageBuildingChain(?ProgramHash, ?ProgramType).

    acceptImageBuildingChain(?ProgramHash, ?ProgramType) :-
      ?ProgramType = "non_image".

    acceptImageBuildingChain(?ProgramHash, ?ProgramType) :-
      acceptImageBuildingChain(?ProgramHash).

    acceptImageBuildingChain(?ImageID) :-
      ?Endorser: tapconImage(?ImageID).
      trustCloudProvider(?Endorser).

    acceptImageBuildingChain(?ImageID) :-
      ?Endorser: buildVMImg(?BuilderImageID, ?ImageID),
      trustedCloudProvider(?Endorser).
      acceptImageBuildingChain(?BuilderImageID).

    label('image-building-chain-policy').
  }.

definit imageBuildingChainPolicySet().

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
  ?DataPolicy := label("tapcon-data-policy"),
  ?AttestationPolicy := label("tapcon-attestation-policy"),
  ?ProgramPrivilegePolicy := label("program-access-privilege-policy"),
  ?TrustedHostImagePolicy := label("trusted-host-image-policy"),
  ?ImageBuildingChainPolicy := label("image-building-chain-policy"), 
  {
    trustedCloudProvider($IaaS).
    link($DataPolicy).
    link($AttestationPolicy).
    link($ProgramPrivilegePolicy).
    link($TrustedHostImagePolicy).
    link($ImageBuildingChainPolicy).
    label("tapcon/anchor-set").
  }.

definit anchorSet().
