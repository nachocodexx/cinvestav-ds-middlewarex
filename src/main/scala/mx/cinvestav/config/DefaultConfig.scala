package mx.cinvestav.config

case class Node(nodeId:String,url:String,role:String)

case class DefaultConfig(
                        nodeId:String,
                        workloadPath:String,
                        nodes: List[Node],
                        loadBalancer:String
                        )
