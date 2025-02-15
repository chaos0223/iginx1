/*
 * IGinX - the polystore system with high performance
 * Copyright (C) Tsinghua University
 * TSIGinX@gmail.com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package cn.edu.tsinghua.iginx.logical.optimizer.rules;

import cn.edu.tsinghua.iginx.engine.shared.function.FunctionCall;
import cn.edu.tsinghua.iginx.engine.shared.function.system.Max;
import cn.edu.tsinghua.iginx.engine.shared.function.system.Min;
import cn.edu.tsinghua.iginx.engine.shared.operator.*;
import cn.edu.tsinghua.iginx.engine.shared.operator.type.OperatorType;
import cn.edu.tsinghua.iginx.engine.shared.source.OperatorSource;
import cn.edu.tsinghua.iginx.logical.optimizer.core.RuleCall;
import cn.edu.tsinghua.iginx.utils.Pair;
import com.google.auto.service.AutoService;
import java.util.*;

/** 该类实现了GroupBy节点中函数中的Distinct的消除。该规则用于消除函数中的Distinct，比如在min(distinct a)中的distinct是不必要的。 */
@AutoService(Rule.class)
public class FunctionDistinctEliminateRule extends Rule {

  private static final Set<String> functionSet = new HashSet<>(Arrays.asList(Min.MIN, Max.MAX));

  public FunctionDistinctEliminateRule() {
    /*
     * we want to match the topology like:
     *     GroupBy/DownSample/Row/Mapping/SetTransform
     *               |
     *              Any
     */
    super(
        "FunctionDistinctEliminateRule",
        "DistinctEliminateRule",
        operand(AbstractUnaryOperator.class, any()));
  }

  public boolean matches(RuleCall call) {
    AbstractUnaryOperator unaryOperator = (AbstractUnaryOperator) call.getMatchedRoot();

    if (!OperatorType.isHasFunction(unaryOperator.getType())) {
      return false;
    }

    List<FunctionCall> functionCallList = getFunctionCallList(unaryOperator);
    if (!isDistinct(unaryOperator)) {
      return false;
    }
    if (functionCallList.stream()
        .anyMatch(
            functionCall -> !functionSet.contains(functionCall.getFunction().getIdentifier()))) {
      return false;
    }

    return true;
  }

  public void onMatch(RuleCall call) {
    AbstractUnaryOperator unaryOperator = (AbstractUnaryOperator) call.getMatchedRoot();
    List<FunctionCall> functionCallList = getFunctionCallList(unaryOperator);
    List<Pair<String, String>> renameMap = new ArrayList<>();

    // 将函数中的distinct去掉
    for (FunctionCall functionCall : functionCallList) {
      String oldName = functionCall.getFunctionStr();
      functionCall.getParams().setDistinct(false);
      renameMap.add(new Pair<>(functionCall.getFunctionStr(), oldName));
    }

    // 添加一个rename节点，将不带distinct的函数结果重命名为原来的函数名，否则显示与用户输入的SQL不一致
    Rename rename = new Rename(new OperatorSource(unaryOperator), renameMap);

    call.transformTo(rename);
  }

  private List<FunctionCall> getFunctionCallList(AbstractUnaryOperator unaryOperator) {
    switch (unaryOperator.getType()) {
      case GroupBy:
        return ((GroupBy) unaryOperator).getFunctionCallList();
      case Downsample:
        return ((Downsample) unaryOperator).getFunctionCallList();
      case RowTransform:
        return ((RowTransform) unaryOperator).getFunctionCallList();
      case MappingTransform:
        return ((MappingTransform) unaryOperator).getFunctionCallList();
      case SetTransform:
        return ((SetTransform) unaryOperator).getFunctionCallList();
      default:
        return new ArrayList<>();
    }
  }

  private boolean isDistinct(AbstractUnaryOperator operator) {
    List<FunctionCall> functionCallList = getFunctionCallList(operator);
    for (FunctionCall functionCall : functionCallList) {
      if (functionCall.getParams().isDistinct()) {
        return true;
      }
    }
    return false;
  }
}
