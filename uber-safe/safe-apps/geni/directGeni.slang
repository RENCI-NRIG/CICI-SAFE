defenv GeniRoot :- u'ouLCPTetVJYnAUbfE0n_W6ElYxITXG8lKD6bPCYlbsM'.
defenv Idp      :- u'As1r56WiIHkxzci8valHOGyDYNXD4GPWbmMqbHDXwp8'.
defenv PA       :- u'q-O1jJKpE7ZMVyf-3nKhYgEOjGvE16UJtA6h45DxgKE'.
defenv SA       :- u'825QDOgLcetdp4qOvGZRc9nTqSuQPQdRmEg5j-nXG44'.
defenv CP       :- u'ay1HJXe5v38PG0uGOgU10G2Pstn3wcE9-7mcXb5JmxQ'.

defenv PI       :- u'8Hlq_5XH7C25YCpPklHk3XNn4B-asWRqiAdPJdyXFVY'.
defenv User1    :- u'1RF6d7jpvAVVsByofu7XLx4-O9Qhbd5eXLGhFSGQTps'.
defenv User2    :- u'DCR9fcoV0bKPv6ZybvLBnKP7mtDdCtIE1xger5P416s'.

defcon direct() :- 
  direct{
 
u'q-O1jJKpE7ZMVyf-3nKhYgEOjGvE16UJtA6h45DxgKE': member(?User, ?Project, ?Delegatable) :- ?Delegator: delegateMember(?User, ?Project, ?Delegatable), u'q-O1jJKpE7ZMVyf-3nKhYgEOjGvE16UJtA6h45DxgKE': member(?Delegator, ?Project, true).
u'q-O1jJKpE7ZMVyf-3nKhYgEOjGvE16UJtA6h45DxgKE': member(?User, ?Project, true) :- u'q-O1jJKpE7ZMVyf-3nKhYgEOjGvE16UJtA6h45DxgKE': owner(?User, ?Project).
u'q-O1jJKpE7ZMVyf-3nKhYgEOjGvE16UJtA6h45DxgKE': project('8Hlq_5XH7C25YCpPklHk3XNn4B-asWRqiAdPJdyXFVY:projectGuid').
u'825QDOgLcetdp4qOvGZRc9nTqSuQPQdRmEg5j-nXG44': controls(?Subject, ?Slice) :- u'825QDOgLcetdp4qOvGZRc9nTqSuQPQdRmEg5j-nXG44': controls(?Subject, ?Slice, _).
u'825QDOgLcetdp4qOvGZRc9nTqSuQPQdRmEg5j-nXG44': controlPrivilege(?Subject, ?Slice, stop) :- u'825QDOgLcetdp4qOvGZRc9nTqSuQPQdRmEg5j-nXG44': slice(?Slice, ?Project, standard), u'825QDOgLcetdp4qOvGZRc9nTqSuQPQdRmEg5j-nXG44': gmoc(?Subject).
u'825QDOgLcetdp4qOvGZRc9nTqSuQPQdRmEg5j-nXG44': controlPrivilege(?Subject, ?Slice, info) :- u'825QDOgLcetdp4qOvGZRc9nTqSuQPQdRmEg5j-nXG44': slice(?Slice, ?Project, standard), u'825QDOgLcetdp4qOvGZRc9nTqSuQPQdRmEg5j-nXG44': gmoc(?Subject).
u'825QDOgLcetdp4qOvGZRc9nTqSuQPQdRmEg5j-nXG44': owner('1RF6d7jpvAVVsByofu7XLx4-O9Qhbd5eXLGhFSGQTps', '825QDOgLcetdp4qOvGZRc9nTqSuQPQdRmEg5j-nXG44:sliceGuid').
u'q-O1jJKpE7ZMVyf-3nKhYgEOjGvE16UJtA6h45DxgKE': owner('8Hlq_5XH7C25YCpPklHk3XNn4B-asWRqiAdPJdyXFVY', '8Hlq_5XH7C25YCpPklHk3XNn4B-asWRqiAdPJdyXFVY:projectGuid').
u'ay1HJXe5v38PG0uGOgU10G2Pstn3wcE9-7mcXb5JmxQ': geniUser(?User) :- u'ay1HJXe5v38PG0uGOgU10G2Pstn3wcE9-7mcXb5JmxQ': identityProvider(?IdP), ?IdP: geniUser(?User).
u'As1r56WiIHkxzci8valHOGyDYNXD4GPWbmMqbHDXwp8': geniUser('1RF6d7jpvAVVsByofu7XLx4-O9Qhbd5eXLGhFSGQTps').
u'As1r56WiIHkxzci8valHOGyDYNXD4GPWbmMqbHDXwp8': geniUser('8Hlq_5XH7C25YCpPklHk3XNn4B-asWRqiAdPJdyXFVY').
u'825QDOgLcetdp4qOvGZRc9nTqSuQPQdRmEg5j-nXG44': controls(?Subject, ?Slice, true) :- u'825QDOgLcetdp4qOvGZRc9nTqSuQPQdRmEg5j-nXG44': owner(?Subject, ?Slice).
u'825QDOgLcetdp4qOvGZRc9nTqSuQPQdRmEg5j-nXG44': controls(?Subject, ?Slice, ?Delegatable) :- ?Delegator: delegateControl(?Subject, ?Slice, ?Delegatable), u'825QDOgLcetdp4qOvGZRc9nTqSuQPQdRmEg5j-nXG44': controls(?Delegator, ?Slice, true).
u'ouLCPTetVJYnAUbfE0n_W6ElYxITXG8lKD6bPCYlbsM': aggregate('ay1HJXe5v38PG0uGOgU10G2Pstn3wcE9-7mcXb5JmxQ') :- u'ouLCPTetVJYnAUbfE0n_W6ElYxITXG8lKD6bPCYlbsM': spec('geni endorses an aggregate ay1HJXe5v38PG0uGOgU10G2Pstn3wcE9-7mcXb5JmxQ').
u'ay1HJXe5v38PG0uGOgU10G2Pstn3wcE9-7mcXb5JmxQ': aggregate(?Agg) :- u'ay1HJXe5v38PG0uGOgU10G2Pstn3wcE9-7mcXb5JmxQ': geniRoot(?Geni), ?Geni: aggregate(?Agg).
u'ay1HJXe5v38PG0uGOgU10G2Pstn3wcE9-7mcXb5JmxQ': geniProxy(?GeniProxy) :- u'ay1HJXe5v38PG0uGOgU10G2Pstn3wcE9-7mcXb5JmxQ': geniRoot(?Geni), ?Geni: geniProxy(?GeniProxy).
u'825QDOgLcetdp4qOvGZRc9nTqSuQPQdRmEg5j-nXG44': slice('825QDOgLcetdp4qOvGZRc9nTqSuQPQdRmEg5j-nXG44:sliceGuid', '8Hlq_5XH7C25YCpPklHk3XNn4B-asWRqiAdPJdyXFVY:projectGuid', standard).
u'ouLCPTetVJYnAUbfE0n_W6ElYxITXG8lKD6bPCYlbsM': sliceAuthority('825QDOgLcetdp4qOvGZRc9nTqSuQPQdRmEg5j-nXG44') :- u'ouLCPTetVJYnAUbfE0n_W6ElYxITXG8lKD6bPCYlbsM': spec('geni endorses a slice authority 825QDOgLcetdp4qOvGZRc9nTqSuQPQdRmEg5j-nXG44').
u'ay1HJXe5v38PG0uGOgU10G2Pstn3wcE9-7mcXb5JmxQ': sliceAuthority(?SA) :- u'ay1HJXe5v38PG0uGOgU10G2Pstn3wcE9-7mcXb5JmxQ': geniRoot(?Geni), ?Geni: sliceAuthority(?SA).
//u'825QDOgLcetdp4qOvGZRc9nTqSuQPQdRmEg5j-nXG44': principal(u'MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA9N8Pvatgh1jfAYPpZD1tB_gnCYi6eULkszokDqtw20W-ubRgHIJ-46A5gnzPMjvaHkz_COPA4u-PLw5sE2uO_h9cDe41vDW_oKcKSZXNQCFeJFNXUeItiVaZt8miIlq_2ZxILkzWUrVLQJLaOEWVcDVChDMff4ikJy5mgE-lkt5ktnN4vhQfGd7Gm_QIY0917NOTl7u_xl0m4VbJLeO2YdoSCZ0RwY1uqjFC5MsdozpH2t3DBgFJ0-WZV80ybuCLMIyCJVTLqzDGqcBpa2MB8gbkmrcm_2AfdDVNicRhvWZpyGLHeJyJqeLyt-GI_eQd-7-iZi2tFIedODypFd46OQIDAQAB') :- u'825QDOgLcetdp4qOvGZRc9nTqSuQPQdRmEg5j-nXG44': spec('identity set for sa').
//u'q-O1jJKpE7ZMVyf-3nKhYgEOjGvE16UJtA6h45DxgKE': principal(u'MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEApKb0GjOrXija-aceaHb_Q0nyhIc4fIkAU_TIAEz7mAORZF0ezP73tvCHi2N_ZjXXtceIVMVcLH4Yo4gm_nzWkY-BomQg8c6F6HslzM1mo4CANeloz46e1Un2c9ngCE7CpjTfFktR3Xinajqk-j-1WOMDTB8_3wGa7KP-FdyJ6lYXC7IqcBjciLAzLUeudNnzrGEXPZHiLq3Ve1SKcGFsJPgX87A7RVnnjNsjL9y-pVqrFW_rzRliw1XSwdcaGHOAvm2IEfwZ-EPz7uyk4tbI-_GaLhus2QGsanVpn4hB2agSRSrEUevu4nB0TkwEV4RHaoE-nJmVDVKWYJ97-bVZnwIDAQAB') :- u'q-O1jJKpE7ZMVyf-3nKhYgEOjGvE16UJtA6h45DxgKE': spec('identity set for pa').
//u'ouLCPTetVJYnAUbfE0n_W6ElYxITXG8lKD6bPCYlbsM': principal(u'MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAvUhFsgiTnfaC3YpbSk_V_jvpPOY_TdyNF-6aJmQnYGecPdj0qx4tVbFNkDvEyji9EHucl3XQoSWYNaiTFaDMhOkL2F9-VFiomldvrbpY0Qy95fgIPz9VCyHX8IAKQhhEkpMyLD8GPsCjRk3IS-G27YR7Z7-aPVkMPwU-as5Wo82a79FNmbQKc5CrANA2hZ9PfbEDdf4EY4JN6vDH5vygoK3R6mPk9daK82pjZ2L-bjYYKqoNIBZQ77tdi067md4fQhn7TKRQSdSYEytfSnQtr46zXWNiFryDzNqOjWqsyYYRE63AfEujOXozc8YZl98X5ShrAvnBBFwFc5A1CYAVywIDAQAB') :- u'ouLCPTetVJYnAUbfE0n_W6ElYxITXG8lKD6bPCYlbsM': spec('identity set for geniRoot').
//u'ay1HJXe5v38PG0uGOgU10G2Pstn3wcE9-7mcXb5JmxQ': principal(u'MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAv5jCkWAWriiZQNhcxy0qn-BwC5LZIPK672WSlXRBtasW4-X2cCdGMYzMRwNMlh60kb7Bvl8uvP8bnKKZO5pMMh1BgB5O2WvzlE0F-fVuLrRHyPnfhtAWq2gC_3ut4GJ_yCAvSffxi9L390BzvUGsB9qO8VSxsQ2zvReFOkzrsip5x3YlffA3-pWEM2cyPYd9-2r2n35JySuz_QNiNhZHrIhqczx0Lu690Gt49K0psaxHAC8owrqIxPSz4dWypd3UFMPO8A7XF7-8dEpYR9HfNktu_grm0EHg9b5Qd-FEXa_sFLKVr7z7qViJ8JdUU_QahVNf7I0uSPDOz34pX4MoiQIDAQAB') :- u'ay1HJXe5v38PG0uGOgU10G2Pstn3wcE9-7mcXb5JmxQ': spec('identity set for cp').
//u'As1r56WiIHkxzci8valHOGyDYNXD4GPWbmMqbHDXwp8': principal(u'MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAyF1Z_LaLSd17K6ZzfsU4vobV3EAYj0S47kGfu7EFHp32yZNj1TvsCqFuKjWVkavCwFrvDM5b9hpHyFeKSkR4t9m-vIbugKpGXfyiIs0AkWlP9w4h4jkaf32qhl2IFbiMkP7hj2QkulUmiZHI7bH_ZPrveAHWznFFyXo8AtNNvdJ7ndLDQvrWHJQ9nl3PQikQ6S3ica8E3y-IHQ7H-i_XZRfAm7jF_c0rUf5NbjeGcefKhhBFHUUI8jiDEm5XjuQfRufDsbSFBkQlyeD-6MJ933KGxeZ4_3VJsGJ79UBoq7Pjnt_99jJzeXgXUhmcWj85qyRFNJGMfg5oQu7yohz2ZQIDAQAB') :- u'As1r56WiIHkxzci8valHOGyDYNXD4GPWbmMqbHDXwp8': spec('identity set for idp').
u'ay1HJXe5v38PG0uGOgU10G2Pstn3wcE9-7mcXb5JmxQ': gmoc(?GeniMOC) :- u'ay1HJXe5v38PG0uGOgU10G2Pstn3wcE9-7mcXb5JmxQ': geniRoot(?Geni), ?Geni: gmoc(?GeniMOC).
u'825QDOgLcetdp4qOvGZRc9nTqSuQPQdRmEg5j-nXG44': controlPrivilege(?Subject, ?Slice, stop, true) :- u'825QDOgLcetdp4qOvGZRc9nTqSuQPQdRmEg5j-nXG44': slice(?Slice, ?Project, standard), ?PA := u'825QDOgLcetdp4qOvGZRc9nTqSuQPQdRmEg5j-nXG44': rootId(?Project), u'825QDOgLcetdp4qOvGZRc9nTqSuQPQdRmEg5j-nXG44': projectAuthority(?PA), ?PA: project(?Project), u'825QDOgLcetdp4qOvGZRc9nTqSuQPQdRmEg5j-nXG44': owner(?Subject, ?Project).
u'825QDOgLcetdp4qOvGZRc9nTqSuQPQdRmEg5j-nXG44': controlPrivilege(?Subject, ?Slice, ?Priv, ?Delegatable) :- ?Delegator: delegateControlPrivilege(?Subject, ?Slice, ?Priv, ?Delegatable), u'825QDOgLcetdp4qOvGZRc9nTqSuQPQdRmEg5j-nXG44': controlPrivilege(?Delegator, ?Slice, ?Priv, true).
u'825QDOgLcetdp4qOvGZRc9nTqSuQPQdRmEg5j-nXG44': controlPrivilege(?Subject, ?Slice, instantiate, ?Delegator) :- u'825QDOgLcetdp4qOvGZRc9nTqSuQPQdRmEg5j-nXG44': controls(?Subject, ?Slice, ?Delegator).
u'825QDOgLcetdp4qOvGZRc9nTqSuQPQdRmEg5j-nXG44': controlPrivilege(?Subject, ?Slice, info, ?Delegatable) :- u'825QDOgLcetdp4qOvGZRc9nTqSuQPQdRmEg5j-nXG44': slice(?Slice, ?Project, standard), ?PA := u'825QDOgLcetdp4qOvGZRc9nTqSuQPQdRmEg5j-nXG44': rootId(?Project), u'825QDOgLcetdp4qOvGZRc9nTqSuQPQdRmEg5j-nXG44': projectAuthority(?PA), ?PA: project(?Project), ?PA: memberPrivilege(?Subject, ?Project, info, ?Delegatable).
u'ouLCPTetVJYnAUbfE0n_W6ElYxITXG8lKD6bPCYlbsM': projectAuthority('q-O1jJKpE7ZMVyf-3nKhYgEOjGvE16UJtA6h45DxgKE') :- u'ouLCPTetVJYnAUbfE0n_W6ElYxITXG8lKD6bPCYlbsM': spec('geni endorses a project authority q-O1jJKpE7ZMVyf-3nKhYgEOjGvE16UJtA6h45DxgKE').
u'ay1HJXe5v38PG0uGOgU10G2Pstn3wcE9-7mcXb5JmxQ': projectAuthority(?PA) :- u'ay1HJXe5v38PG0uGOgU10G2Pstn3wcE9-7mcXb5JmxQ': geniRoot(?Geni), ?Geni: projectAuthority(?PA).
u'ay1HJXe5v38PG0uGOgU10G2Pstn3wcE9-7mcXb5JmxQ': geniPI(?User) :- u'ay1HJXe5v38PG0uGOgU10G2Pstn3wcE9-7mcXb5JmxQ': identityProvider(?IdP), ?IdP: geniPI(?User).
u'As1r56WiIHkxzci8valHOGyDYNXD4GPWbmMqbHDXwp8': geniPI('8Hlq_5XH7C25YCpPklHk3XNn4B-asWRqiAdPJdyXFVY').
u'ouLCPTetVJYnAUbfE0n_W6ElYxITXG8lKD6bPCYlbsM': identityProvider('As1r56WiIHkxzci8valHOGyDYNXD4GPWbmMqbHDXwp8') :- u'ouLCPTetVJYnAUbfE0n_W6ElYxITXG8lKD6bPCYlbsM': spec('geni endorses an identity provider As1r56WiIHkxzci8valHOGyDYNXD4GPWbmMqbHDXwp8').
u'ay1HJXe5v38PG0uGOgU10G2Pstn3wcE9-7mcXb5JmxQ': identityProvider(?IdP) :- u'ay1HJXe5v38PG0uGOgU10G2Pstn3wcE9-7mcXb5JmxQ': geniRoot(?Geni), ?Geni: identityProvider(?IdP).
u'q-O1jJKpE7ZMVyf-3nKhYgEOjGvE16UJtA6h45DxgKE': member(?User, ?Project) :- u'q-O1jJKpE7ZMVyf-3nKhYgEOjGvE16UJtA6h45DxgKE': member(?User, ?Project, _).
u'q-O1jJKpE7ZMVyf-3nKhYgEOjGvE16UJtA6h45DxgKE': memberPrivilege(?User, ?Project, ?Priv, ?Delegatable) :- ?Delegator: delegateMemberPrivilege(?User, ?Project, ?Priv, ?Delegatable), u'q-O1jJKpE7ZMVyf-3nKhYgEOjGvE16UJtA6h45DxgKE': memberPrivilege(?Delegator, ?Project, ?Priv, true).
u'q-O1jJKpE7ZMVyf-3nKhYgEOjGvE16UJtA6h45DxgKE': memberPrivilege(?User, ?Project, instantiate, ?Delegatable) :- u'q-O1jJKpE7ZMVyf-3nKhYgEOjGvE16UJtA6h45DxgKE': member(?User, ?Project, ?Delegatable).


     sliverInstantiate(?Slice, ?Subject, ?Project) :-
       geniUser(?Subject),
       ?SA := rootId(?Slice),
       ?SA: slice(?Slice, ?Project, standard),
       sliceAuthority(?SA), 
       ?SA: controlPrivilege(?Subject, ?Slice, instantiate, _).

     geniRoot($GeniRoot).
     goal()         :- sliverInstantiate('825QDOgLcetdp4qOvGZRc9nTqSuQPQdRmEg5j-nXG44:sliceGuid', '1RF6d7jpvAVVsByofu7XLx4-O9Qhbd5eXLGhFSGQTps', '8Hlq_5XH7C25YCpPklHk3XNn4B-asWRqiAdPJdyXFVY:projectGuid').
   }
end

defguard directGeni() :-
  {
     import(direct).
     goal()?
  }
end

definit direct().
